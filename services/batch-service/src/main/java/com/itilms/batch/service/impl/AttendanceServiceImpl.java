package com.itilms.batch.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.dto.request.MarkAttendanceRequest;
import com.itilms.batch.dto.response.AttendanceResponse;
import com.itilms.batch.dto.response.AttendanceSummaryResponse;
import com.itilms.batch.entity.Attendance;
import com.itilms.batch.entity.AttendanceSource;
import com.itilms.batch.entity.AttendanceStatus;
import com.itilms.batch.entity.ClassSession;
import com.itilms.batch.entity.Enrollment;
import com.itilms.batch.repository.AttendanceRepository;
import com.itilms.batch.repository.ClassSessionRepository;
import com.itilms.batch.repository.EnrollmentRepository;
import com.itilms.batch.service.AttendanceService;
import com.itilms.batch.service.BatchService;
import com.itilms.common.event.AttendanceMarkedEvent;
import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.LiveAttendanceComputedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private static final String SERVICE_NAME = "batch-service";

    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BatchService batchService;
    private final EventPublisher events;

    /** Below this, a student appears on the attendance-alerts widget. */
    @Value("${itilms.attendance.alert-threshold:75}")
    private double alertThreshold;

    // -----------------------------------------------------------------
    // Marking
    // -----------------------------------------------------------------

    @Override
    @Transactional
    public List<AttendanceResponse> mark(Long sessionId, MarkAttendanceRequest request) {
        ClassSession session = requireSession(sessionId);
        requireCanMark(session.getBatchId());

        if (!session.getStatus().allowsAttendance()) {
            throw new BusinessRuleException(
                    "This session is %s, so attendance cannot be recorded for it."
                            .formatted(session.getStatus().name().toLowerCase()));
        }

        boolean isCorrection = session.isAttendanceMarked();
        if (isCorrection && (request.correctionReason() == null || request.correctionReason().isBlank())) {
            // Doc S14: attendance corrections must be audited, and an audit
            // entry with no reason answers nothing when it is read back later.
            throw new BusinessRuleException(
                    "Attendance for this session has already been saved. "
                            + "Provide a correctionReason to change it.");
        }

        // The register is the batch's active roster. Marking someone who is not
        // enrolled would create attendance for a student with no place in the
        // class, which then corrupts their percentage.
        Set<Long> roster = new HashSet<>(enrollmentRepository.findActiveStudentIds(session.getBatchId()));

        Map<Long, Attendance> existing = new HashMap<>();
        attendanceRepository.findBySessionId(sessionId)
                .forEach(record -> existing.put(record.getStudentId(), record));

        Long actingUserId = SecurityUtils.currentUserId();
        List<Attendance> saved = new ArrayList<>(request.entries().size());
        List<AttendanceMarkedEvent.Entry> eventEntries = new ArrayList<>(request.entries().size());
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();

        for (var entry : request.entries()) {
            if (!roster.contains(entry.studentId())) {
                throw new BusinessRuleException(
                        "Student %d is not on this batch's active register".formatted(entry.studentId()));
            }

            AttendanceStatus status = parseStatus(entry.status());
            Attendance record = existing.get(entry.studentId());

            if (record == null) {
                record = Attendance.builder()
                        .sessionId(sessionId)
                        .studentId(entry.studentId())
                        .status(status)
                        .remark(trim(entry.remark()))
                        .source(AttendanceSource.MANUAL)
                        .markedAt(Instant.now())
                        .markedBy(actingUserId)
                        .build();
            } else if (record.getStatus() != status || isCorrection) {
                before.put(String.valueOf(entry.studentId()), record.getStatus().name());
                after.put(String.valueOf(entry.studentId()), status.name());
                record.correct(status, trim(entry.remark()), actingUserId);
            }

            saved.add(record);
            eventEntries.add(new AttendanceMarkedEvent.Entry(entry.studentId(), status.name()));
        }

        attendanceRepository.saveAll(saved);

        session.setAttendanceMarked(true);
        session.setAttendanceAuto(false);
        sessionRepository.save(session);

        events.publishAfterCommit(KafkaTopics.ATTENDANCE_MARKED, new AttendanceMarkedEvent(
                DomainEvent.newId(), Instant.now(),
                sessionId, session.getBatchId(), session.getSessionDate(),
                actingUserId, isCorrection, eventEntries));

        if (isCorrection) {
            after.put("reason", request.correctionReason());
            events.audit(SERVICE_NAME, "ATTENDANCE_CORRECTED", "ClassSession", sessionId, before, after);
            log.info("Attendance corrected for session {} by user {}: {}",
                    sessionId, actingUserId, request.correctionReason());
        } else {
            events.audit(SERVICE_NAME, "ATTENDANCE_MARKED", "ClassSession", sessionId, null,
                    Map.of("entries", eventEntries.size()));
        }

        return toResponses(saved, session);
    }

    // -----------------------------------------------------------------
    // Live-class attendance
    // -----------------------------------------------------------------

    /**
     * Applies attendance derived from a LiveKit room.
     *
     * <p>The rule that matters here is precedence. A trainer's judgement always
     * wins: a student whose connection dropped but who was clearly participating
     * can be marked present by hand, and this must never undo that. So rows that
     * a human created or corrected are left untouched, and only genuinely
     * missing or previously-automatic rows are written.
     */
    @Override
    @Transactional
    public void applyLiveAttendance(LiveAttendanceComputedEvent event) {
        ClassSession session = sessionRepository.findById(event.classSessionId()).orElse(null);
        if (session == null) {
            log.warn("Live attendance received for unknown session {}", event.classSessionId());
            return;
        }

        Set<Long> roster = new HashSet<>(enrollmentRepository.findActiveStudentIds(session.getBatchId()));

        Map<Long, Attendance> existing = new HashMap<>();
        attendanceRepository.findBySessionId(session.getId())
                .forEach(record -> existing.put(record.getStudentId(), record));

        List<Attendance> toSave = new ArrayList<>();
        List<AttendanceMarkedEvent.Entry> eventEntries = new ArrayList<>();
        int skippedManual = 0;

        for (var entry : event.entries()) {
            if (!roster.contains(entry.studentId())) {
                continue;
            }

            Attendance record = existing.get(entry.studentId());
            if (record != null
                    && (record.getSource() == AttendanceSource.MANUAL || record.wasCorrected())) {
                skippedManual++;
                continue;
            }

            AttendanceStatus status = parseStatus(entry.status());
            if (record == null) {
                record = Attendance.builder()
                        .sessionId(session.getId())
                        .studentId(entry.studentId())
                        .status(status)
                        .source(AttendanceSource.LIVE_CLASS)
                        .attendedMinutes(entry.attendedSeconds() / 60)
                        .remark("Recorded automatically from the live class (%d%% of the session)"
                                .formatted(entry.attendancePercent()))
                        .markedAt(Instant.now())
                        .build();
            } else {
                record.setStatus(status);
                record.setAttendedMinutes(entry.attendedSeconds() / 60);
                record.setSource(AttendanceSource.LIVE_CLASS);
                record.setMarkedAt(Instant.now());
            }

            toSave.add(record);
            eventEntries.add(new AttendanceMarkedEvent.Entry(entry.studentId(), status.name()));
        }

        // Anyone enrolled who never appeared in the room is absent. Without this
        // an online class would only ever record the people who showed up, and
        // absences would silently vanish from the percentage.
        Set<Long> seen = event.entries().stream()
                .map(LiveAttendanceComputedEvent.Entry::studentId)
                .collect(java.util.stream.Collectors.toSet());

        for (Long studentId : roster) {
            if (seen.contains(studentId)) {
                continue;
            }
            Attendance record = existing.get(studentId);
            if (record != null) {
                continue;   // already marked, by a human or an earlier run
            }
            toSave.add(Attendance.builder()
                    .sessionId(session.getId())
                    .studentId(studentId)
                    .status(AttendanceStatus.ABSENT)
                    .source(AttendanceSource.LIVE_CLASS)
                    .attendedMinutes(0)
                    .remark("Did not join the live class")
                    .markedAt(Instant.now())
                    .build());
            eventEntries.add(new AttendanceMarkedEvent.Entry(studentId, AttendanceStatus.ABSENT.name()));
        }

        if (toSave.isEmpty()) {
            log.debug("Live attendance for session {} changed nothing", session.getId());
            return;
        }

        attendanceRepository.saveAll(toSave);
        session.setAttendanceMarked(true);
        session.setAttendanceAuto(true);
        sessionRepository.save(session);

        events.publishAfterCommit(KafkaTopics.ATTENDANCE_MARKED, new AttendanceMarkedEvent(
                DomainEvent.newId(), Instant.now(),
                session.getId(), session.getBatchId(), session.getSessionDate(),
                null, false, eventEntries));

        events.audit(SERVICE_NAME, "ATTENDANCE_AUTO_MARKED", "ClassSession", session.getId(), null,
                Map.of("written", toSave.size(), "preservedManualMarks", skippedManual,
                        "liveSessionId", event.liveSessionId()));

        log.info("Live attendance applied to session {}: {} record(s) written, {} manual mark(s) preserved",
                session.getId(), toSave.size(), skippedManual);
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> forSession(Long sessionId) {
        ClassSession session = requireSession(sessionId);
        requireCanView(session.getBatchId());
        return toResponses(attendanceRepository.findBySessionId(sessionId), session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> studentHistory(Long studentId, Long batchId,
                                                   LocalDate from, LocalDate to) {
        SecurityUtils.requireStudentOwnershipOrStaff(studentId);

        LocalDate start = from != null ? from : LocalDate.now().minusMonths(3);
        LocalDate end = to != null ? to : LocalDate.now();

        return attendanceRepository.findStudentHistory(studentId, batchId, start, end).stream()
                .map(row -> {
                    Attendance attendance = (Attendance) row[0];
                    ClassSession session = (ClassSession) row[1];
                    return AttendanceResponse.from(attendance, null,
                            session.getSessionDate(), session.getTopic());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceSummaryResponse studentSummary(Long studentId, Long batchId) {
        SecurityUtils.requireStudentOwnershipOrStaff(studentId);

        Object[] totals = attendanceRepository.attendanceTotalsForBatch(studentId, batchId);
        int attended = toInt(totals, 0);
        int total = toInt(totals, 1);

        String name = enrollmentRepository
                .findByStudentIdAndBatchIdAndStatus(studentId, batchId,
                        com.itilms.batch.entity.EnrollmentStatus.ACTIVE)
                .map(Enrollment::getStudentName)
                .orElse(null);

        return AttendanceSummaryResponse.of(studentId, name, batchId, attended, total, alertThreshold);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceSummaryResponse> batchSummary(Long batchId) {
        requireCanView(batchId);

        Map<Long, String> names = new HashMap<>();
        enrollmentRepository.findActiveRoster(batchId)
                .forEach(enrollment -> names.put(enrollment.getStudentId(), enrollment.getStudentName()));

        Map<Long, int[]> totals = new HashMap<>();
        attendanceRepository.attendanceTotalsForAllInBatch(batchId).forEach(row ->
                totals.put((Long) row[0], new int[]{toInt(row[1]), toInt(row[2])}));

        // Driven from the roster, not from the attendance rows: a student who
        // has never been marked still belongs on the list, at 0 of 0.
        return names.entrySet().stream()
                .map(entry -> {
                    int[] counts = totals.getOrDefault(entry.getKey(), new int[]{0, 0});
                    return AttendanceSummaryResponse.of(entry.getKey(), entry.getValue(), batchId,
                            counts[0], counts[1], alertThreshold);
                })
                .sorted(java.util.Comparator.comparing(AttendanceSummaryResponse::attendancePercent))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceSummaryResponse> attendanceAlerts(Long batchId) {
        return batchSummary(batchId).stream()
                .filter(AttendanceSummaryResponse::belowThreshold)
                .toList();
    }

    // -----------------------------------------------------------------
    // Guards and helpers
    // -----------------------------------------------------------------

    private void requireCanMark(Long batchId) {
        AppPrincipal principal = SecurityUtils.requirePrincipal();
        if (principal.isStudent()) {
            throw new ForbiddenOperationException("Students cannot mark attendance");
        }
        if (principal.isTrainer()
                && !batchService.batchIdsForTrainer(principal.profileId()).contains(batchId)) {
            throw new ForbiddenOperationException(
                    "You may only mark attendance for batches assigned to you");
        }
    }

    private void requireCanView(Long batchId) {
        AppPrincipal principal = SecurityUtils.requirePrincipal();
        if (principal.isStudent()) {
            if (!batchService.activeBatchIdsForStudent(principal.profileId()).contains(batchId)) {
                throw new ForbiddenOperationException("You are not enrolled in that batch");
            }
        } else if (principal.isTrainer()
                && !batchService.batchIdsForTrainer(principal.profileId()).contains(batchId)) {
            throw new ForbiddenOperationException("That batch is not assigned to you");
        }
    }

    private List<AttendanceResponse> toResponses(List<Attendance> records, ClassSession session) {
        Map<Long, String> names = new HashMap<>();
        enrollmentRepository.findActiveRoster(session.getBatchId())
                .forEach(enrollment -> names.put(enrollment.getStudentId(), enrollment.getStudentName()));

        return records.stream()
                .map(record -> AttendanceResponse.from(record, names.get(record.getStudentId()),
                        session.getSessionDate(), session.getTopic()))
                .toList();
    }

    private ClassSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
    }

    private AttendanceStatus parseStatus(String value) {
        try {
            return AttendanceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Attendance status must be PRESENT, ABSENT, LATE or EXCUSED");
        }
    }

    /** SUM over no rows returns null, which a plain cast would turn into an NPE. */
    private int toInt(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0;
        }
        return ((Number) row[index]).intValue();
    }

    private int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
