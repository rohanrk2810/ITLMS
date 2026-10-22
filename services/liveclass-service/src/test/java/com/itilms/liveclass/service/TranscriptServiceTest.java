package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.LiveSessionStatus;
import com.itilms.liveclass.entity.LiveTranscriptLine;
import com.itilms.liveclass.repository.LiveSessionRepository;
import com.itilms.liveclass.repository.LiveTranscriptLineRepository;

/** Who may write a class's transcript, when, and what the text file looks like. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranscriptServiceTest {

    private static final long SESSION = 5L;
    private static final Instant START = Instant.parse("2026-10-23T05:00:00Z");

    @Mock private LiveSessionRepository sessions;
    @Mock private LiveTranscriptLineRepository lines;
    @Mock private HostAccess hostAccess;

    private TranscriptService service;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        service = new TranscriptService(sessions, lines, hostAccess);
        session = LiveSession.builder().id(SESSION).batchId(1L).batchCode("JFS-01").courseTitle("Java Full Stack")
                .topic("Loops").sessionDate(LocalDate.of(2026, 10, 23)).status(LiveSessionStatus.LIVE).startedAt(START).build();
        when(sessions.findByIdForUpdate(SESSION)).thenReturn(Optional.of(session));
        when(sessions.findById(SESSION)).thenReturn(Optional.of(session));
        when(lines.highestSeq(SESSION)).thenReturn(0);
        when(lines.existingClientIds(any(), anyCollection())).thenReturn(List.of());
    }

    private static TranscriptService.Incoming phrase(String id, String text) {
        return new TranscriptService.Incoming(id, text, "mr-IN", START.plusSeconds(75).toEpochMilli());
    }

    @Test
    @DisplayName("Phrases from the host are stored in order, with how far into the class they were said")
    void storesPhrasesInOrder() {
        int saved = service.append(SESSION, List.of(phrase("a", "Today we learn loops"), phrase("b", "A loop repeats code")));

        assertThat(saved).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LiveTranscriptLine>> stored = ArgumentCaptor.forClass(List.class);
        verify(lines).saveAll(stored.capture());
        assertThat(stored.getValue()).extracting(LiveTranscriptLine::getSeq).containsExactly(1, 2);
        assertThat(stored.getValue().get(0).getText()).isEqualTo("Today we learn loops");
        assertThat(stored.getValue().get(0).getAtSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(session.getTranscriptLines()).isEqualTo(2);
    }

    @Test
    @DisplayName("Someone who is not the class's host cannot write to its transcript")
    void onlyTheHostMayWrite() {
        doThrow(new ForbiddenOperationException("no")).when(hostAccess).requireHostOf(session);

        assertThatThrownBy(() -> service.append(SESSION, List.of(phrase("a", "hello"))))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(lines, never()).saveAll(any());
    }

    @Test
    @DisplayName("Nothing can be added to the transcript once the class is over")
    void closedWhenTheClassEnds() {
        session.setStatus(LiveSessionStatus.ENDED);

        assertThatThrownBy(() -> service.append(SESSION, List.of(phrase("a", "hello"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("live");
    }

    @Test
    @DisplayName("A phrase sent again after a retry is stored once")
    void retriesAreNotDuplicated() {
        when(lines.existingClientIds(any(), anyCollection())).thenReturn(List.of("a"));

        int saved = service.append(SESSION, List.of(phrase("a", "already stored"), phrase("b", "new")));

        assertThat(saved).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LiveTranscriptLine>> stored = ArgumentCaptor.forClass(List.class);
        verify(lines).saveAll(stored.capture());
        assertThat(stored.getValue()).extracting(LiveTranscriptLine::getText).containsExactly("new");
    }

    @Test
    @DisplayName("Blank phrases are dropped and an oversized batch is refused")
    void blankAndOversized() {
        assertThat(service.append(SESSION, List.of(phrase("a", "   ")))).isZero();

        List<TranscriptService.Incoming> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= TranscriptService.MAX_LINES_PER_REQUEST; i++) {
            tooMany.add(phrase("id" + i, "text " + i));
        }
        assertThatThrownBy(() -> service.append(SESSION, tooMany)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("A class whose transcript is full accepts no more")
    void fullTranscriptIsRefused() {
        session.setTranscriptLines(TranscriptService.MAX_LINES_PER_CLASS);

        assertThatThrownBy(() -> service.append(SESSION, List.of(phrase("a", "more"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("A browser with a wrong clock cannot put a phrase before the class or in the future")
    void clockIsHeldInsideTheClass() {
        Instant now = START.plusSeconds(600);

        assertThat(TranscriptService.secondsInto(START, START.minusSeconds(3600).toEpochMilli(), now)).isZero();
        assertThat(TranscriptService.secondsInto(START, now.plusSeconds(3600).toEpochMilli(), now)).isEqualTo(600);
        assertThat(TranscriptService.secondsInto(START, null, now)).isEqualTo(600);
        assertThat(TranscriptService.secondsInto(START, START.plusSeconds(75).toEpochMilli(), now)).isEqualTo(75);
    }

    @Test
    @DisplayName("The text file has a header and one timestamped line per phrase")
    void rendersATextFile() {
        when(lines.findByLiveSessionIdOrderBySeqAsc(SESSION)).thenReturn(List.of(
                LiveTranscriptLine.builder().seq(1).atSeconds(5).text("Welcome").build(),
                LiveTranscriptLine.builder().seq(2).atSeconds(3725).text("That is all for today").build()));

        var file = service.file(SESSION);

        assertThat(file.fileName()).isEqualTo("class-JFS-01-2026-10-23-transcript.txt");
        assertThat(file.content()).startsWith("Loops\nJava Full Stack - batch JFS-01\n");
        assertThat(file.content()).contains("[00:00:05] Welcome\n").contains("[01:02:05] That is all for today\n");
        verify(hostAccess).requireHostOrEnrolled(session);
    }

    @Test
    @DisplayName("A class with no captured words has no transcript to download")
    void noTranscriptIsNotFound() {
        when(lines.findByLiveSessionIdOrderBySeqAsc(SESSION)).thenReturn(List.of());

        assertThatThrownBy(() -> service.file(SESSION)).isInstanceOf(ResourceNotFoundException.class);
    }
}
