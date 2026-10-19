package com.itilms.course.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LessonProgressPlaybackTest {

    @Test
    void percentIsTheShareActuallyPlayedNotTheFurthestPosition() {
        LessonProgress p = new LessonProgress();
        p.recordWatched(600);                    // skipped to the end
        p.recordPlayback(150, 600, 20, 1);       // but only played 150 s

        assertThat(p.getWatchedSeconds()).isEqualTo(600);
        assertThat(p.watchedPercent()).isEqualTo(25);
    }

    @Test
    void countersOnlyMoveForwardAndPlayedTimeIsCappedAtTheVideoLength() {
        LessonProgress p = new LessonProgress();
        p.recordPlayback(500, 400, 30, 2);
        p.recordPlayback(100, 400, 5, 0);         // a stale or replayed report

        assertThat(p.getPlayedSeconds()).isEqualTo(400);
        assertThat(p.watchedPercent()).isEqualTo(100);
        assertThat(p.getFaceAwaySeconds()).isEqualTo(30);
        assertThat(p.getFacePauses()).isEqualTo(2);
    }

    @Test
    void nothingIsClaimedUntilTheVideoLengthIsKnown() {
        LessonProgress p = new LessonProgress();
        p.recordPlayback(90, null, null, null);

        assertThat(p.watchedPercent()).isZero();
    }
}
