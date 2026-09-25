package com.itilms.liveclass.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.liveclass.dto.response.StudentParticipationResponse;
import com.itilms.liveclass.entity.LiveParticipant;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.repository.LiveParticipantRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * How often a student joined the live classes held for their batches.
 *
 * <p>Only finished classes count as "held": a class in progress or not yet started cannot have been missed. Time
 * in the room is what LiveKit reported, the same figure attendance is worked out from.
 */
@Service
@RequiredArgsConstructor
public class StudentParticipationService {

    private final LiveSessionRepository sessions;
    private final LiveParticipantRepository participants;

    @Transactional(readOnly = true)
    public StudentParticipationResponse of(Long studentId, Collection<Long> batchIds) {
        if (batchIds.isEmpty()) {
            return new StudentParticipationResponse(0, 0, 0, null, null);
        }
        List<LiveSession> held = sessions.findByBatchIdInAndStatus(batchIds, LiveSessionStatus.ENDED);
        Set<Long> heldIds = held.stream().map(LiveSession::getId).collect(Collectors.toSet());

        // Joined means they were in the room at all, in a class that has since finished.
        List<LiveParticipant> joined = participants.findByStudentId(studentId).stream()
                .filter(p -> heldIds.contains(p.getLiveSessionId()) && p.getFirstJoinedAt() != null)
                .toList();

        int seconds = joined.stream().mapToInt(LiveParticipant::getAttendedSeconds).sum();
        List<Integer> percents = joined.stream().map(LiveParticipant::getAttendancePercent).filter(Objects::nonNull).toList();
        Instant last = joined.stream().map(LiveParticipant::getFirstJoinedAt).max(Comparator.naturalOrder()).orElse(null);

        return new StudentParticipationResponse(held.size(), joined.size(), seconds / 60,
                percents.isEmpty() ? null : (int) Math.round(percents.stream().mapToInt(Integer::intValue).average().orElse(0)),
                last);
    }
}
