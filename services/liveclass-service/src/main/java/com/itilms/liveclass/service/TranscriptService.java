package com.itilms.liveclass.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.LiveTranscriptLine;
import com.itilms.liveclass.repository.LiveSessionRepository;
import com.itilms.liveclass.repository.LiveTranscriptLineRepository;

import lombok.RequiredArgsConstructor;

/**
 * The written record of what the trainer said in a class.
 *
 * <p>The trainer's browser recognises the speech (nothing is sent here but the words), so this service only stores
 * what the class's own host sends while the class is live, and hands it back as one text file to the people who
 * were entitled to the class: its trainer, staff, and the students enrolled in the batch.
 */
@Service
@RequiredArgsConstructor
public class TranscriptService {

    /** More than a very long class could ever say; stops a runaway client filling the table. */
    static final int MAX_LINES_PER_CLASS = 20_000;
    static final int MAX_LINES_PER_REQUEST = 40;
    static final int MAX_TEXT = 1000;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private final LiveSessionRepository sessions;
    private final LiveTranscriptLineRepository lines;
    private final HostAccess hostAccess;

    /** One phrase as the browser sends it. {@code spokenAt} is epoch milliseconds on the browser's clock. */
    public record Incoming(String clientId, String text, String language, Long spokenAt) {
    }

    /** What a finished transcript looks like to a caller who wants to download it. */
    public record TranscriptFile(String fileName, String content) {
    }

    /**
     * Saves finished phrases from the class's host. The class must be live: a transcript is what was said during it,
     * not something that can be added afterwards. Returns how many were new; a phrase already stored (a retry) is skipped.
     */
    @Transactional
    public int append(Long liveSessionId, List<Incoming> incoming) {
        LiveSession session = sessions.findByIdForUpdate(liveSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session", liveSessionId));
        hostAccess.requireHostOf(session);
        if (session.getStatus() != LiveSessionStatus.LIVE) {
            throw new BusinessRuleException("TRANSCRIPT_CLOSED", "Subtitles can only be saved while the class is live.");
        }
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        if (incoming.size() > MAX_LINES_PER_REQUEST) {
            throw new BusinessRuleException("At most " + MAX_LINES_PER_REQUEST + " phrases can be sent at once.");
        }
        if (session.getTranscriptLines() >= MAX_LINES_PER_CLASS) {
            throw new BusinessRuleException("TRANSCRIPT_FULL", "This class's transcript has reached its size limit.");
        }

        List<String> ids = incoming.stream().map(Incoming::clientId).filter(id -> id != null && !id.isBlank()).toList();
        Set<String> known = ids.isEmpty() ? Set.of() : new HashSet<>(lines.existingClientIds(liveSessionId, ids));

        Instant now = Instant.now();
        Instant classStart = session.getStartedAt() != null ? session.getStartedAt() : now;
        int seq = lines.highestSeq(liveSessionId);
        List<LiveTranscriptLine> fresh = new ArrayList<>();
        Set<String> seenInRequest = new HashSet<>();
        for (Incoming phrase : incoming) {
            String text = phrase.text() == null ? "" : phrase.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            String clientId = phrase.clientId() == null || phrase.clientId().isBlank() ? null : cut(phrase.clientId().strip(), 40);
            if (clientId != null && (known.contains(clientId) || !seenInRequest.add(clientId))) {
                continue;
            }
            if (session.getTranscriptLines() + fresh.size() >= MAX_LINES_PER_CLASS) {
                break;
            }
            fresh.add(LiveTranscriptLine.builder()
                    .liveSessionId(liveSessionId)
                    .seq(++seq)
                    .atSeconds(secondsInto(classStart, phrase.spokenAt(), now))
                    .language(phrase.language() == null ? null : cut(phrase.language().strip(), 10))
                    .text(cut(text, MAX_TEXT))
                    .clientId(clientId)
                    .createdAt(now)
                    .build());
        }
        if (fresh.isEmpty()) {
            return 0;
        }
        lines.saveAll(fresh);
        session.setTranscriptLines(session.getTranscriptLines() + fresh.size());
        sessions.save(session);
        return fresh.size();
    }

    /** The transcript as a text file, for the class's host or a student enrolled in it. */
    @Transactional(readOnly = true)
    public TranscriptFile file(Long liveSessionId) {
        LiveSession session = sessions.findById(liveSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session", liveSessionId));
        hostAccess.requireHostOrEnrolled(session);
        List<LiveTranscriptLine> all = lines.findByLiveSessionIdOrderBySeqAsc(liveSessionId);
        if (all.isEmpty()) {
            throw new ResourceNotFoundException("Transcript", liveSessionId);
        }
        return new TranscriptFile(fileName(session), render(session, all));
    }

    static String render(LiveSession session, List<LiveTranscriptLine> all) {
        StringBuilder out = new StringBuilder();
        out.append(session.getTopic() != null && !session.getTopic().isBlank() ? session.getTopic() : session.getCourseTitle())
                .append('\n');
        out.append(session.getCourseTitle()).append(" - batch ").append(session.getBatchCode()).append('\n');
        if (session.getSessionDate() != null) {
            out.append(DATE.format(session.getSessionDate().atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant())).append('\n');
        }
        out.append("Transcript of what the trainer said. It was written by speech recognition, so some words may be wrong.\n\n");
        for (LiveTranscriptLine line : all) {
            out.append('[').append(clock(line.getAtSeconds())).append("] ").append(line.getText()).append('\n');
        }
        return out.toString();
    }

    static String clock(int seconds) {
        return "%02d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    /**
     * Seconds from the start of the class to when the phrase ended. The browser's clock decides it, held between
     * the class start and now so a wrong clock cannot put a phrase before the class or in the future.
     */
    static int secondsInto(Instant classStart, Long spokenAtMillis, Instant now) {
        Instant at = spokenAtMillis == null ? now : Instant.ofEpochMilli(spokenAtMillis);
        if (at.isAfter(now)) {
            at = now;
        }
        if (at.isBefore(classStart)) {
            at = classStart;
        }
        return (int) Math.min(Integer.MAX_VALUE, Duration.between(classStart, at).getSeconds());
    }

    private static String fileName(LiveSession session) {
        String date = session.getSessionDate() == null ? "" : "-" + session.getSessionDate();
        return ("class-" + session.getBatchCode() + date + "-transcript.txt").replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
