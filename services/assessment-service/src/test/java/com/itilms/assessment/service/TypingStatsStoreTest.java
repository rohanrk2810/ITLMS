package com.itilms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.assessment.service.TypingStatsStore.Counts;

/** The trainer's note about pasted code: counted from the edits as they arrive, flagged only when most of it was pasted. */
class TypingStatsStoreTest {

    private final TypingStatsStore store = new TypingStatsStore();

    @Test
    @DisplayName("Edits are counted as typed or pasted; the starting snapshot counts as neither")
    void countsTypedAndPasted() {
        String edits = "[[0,0,-1,\"class A {}\",0],[100,0,0,\"int\",0],[900,3,0,\" main(String[] a) {}\",1],[950,10,0,\"x\",0]]";

        Counts counts = store.count(edits);

        assertThat(counts.typed()).isEqualTo(4);
        assertThat(counts.pasted()).isEqualTo(20);
        assertThat(counts.largestPaste()).isEqualTo(20);
    }

    @Test
    @DisplayName("Something that is not a list of edits counts as nothing, and is not an error")
    void unreadableCountsAsNothing() {
        assertThat(store.count("not json").isEmpty()).isTrue();
        assertThat(store.count("{\"a\":1}").isEmpty()).isTrue();
        assertThat(store.count("[[1,2]]").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("A mostly pasted answer is flagged, with the numbers")
    void flagsMostlyPasted() {
        String note = TypingStatsStore.review(new Counts(40, 260, 200));

        assertThat(note).contains("260 of 300").contains("largest paste 200");
    }

    @Test
    @DisplayName("Ordinary typing, a small paste, or a big answer with little pasted is not flagged")
    void ordinaryTypingNotFlagged() {
        assertThat(TypingStatsStore.review(new Counts(500, 30, 30))).isNull();
        assertThat(TypingStatsStore.review(new Counts(0, 60, 60))).isNull();
        assertThat(TypingStatsStore.review(new Counts(900, 100, 100))).isNull();
        assertThat(TypingStatsStore.review(null)).isNull();
    }
}
