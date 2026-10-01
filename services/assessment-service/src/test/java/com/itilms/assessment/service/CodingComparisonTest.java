package com.itilms.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itilms.assessment.dto.response.CodingRunResponse;
import com.itilms.assessment.entity.CodingRunStat;
import com.itilms.assessment.repository.CodingRunStatRepository;
import com.itilms.assessment.service.CodingComparison.Other;

class CodingComparisonTest {

    private static List<Other> others(int count, int baseMs) {
        return IntStream.range(0, count).mapToObj(i -> new Other(baseMs + i * 10, 1000 + i * 100)).toList();
    }

    @Test
    @DisplayName("Below the minimum sample nothing is compared, and only the count is revealed")
    void tooFewStudents() {
        var c = CodingComparison.compare(others(5, 100), 120, 1500, 20);

        assertThat(c.available()).isFalse();
        assertThat(c.sampleSize()).isEqualTo(6);
        assertThat(c.minimumSample()).isEqualTo(20);
        assertThat(c.runtimeBeatsPercent()).isNull();
        assertThat(c.runtimeBuckets()).isEmpty();
    }

    @Test
    @DisplayName("Beats the percentage of other students who were slower")
    void percentages() {
        // 30 others at 100, 110 ... 390 ms; this run is 200 ms: 19 of the 30 are slower (210..390).
        var c = CodingComparison.compare(others(30, 100), 200, 900, 20);

        assertThat(c.available()).isTrue();
        assertThat(c.sampleSize()).isEqualTo(31);
        assertThat(c.runtimeBeatsPercent()).isEqualTo(63);
        // Memory 900 is lower than every other (1000 and up), so it beats everyone.
        assertThat(c.memoryBeatsPercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("A tie does not count as beating someone")
    void ties() {
        List<Other> same = IntStream.range(0, 25).mapToObj(i -> new Other(100, 500)).toList();

        var c = CodingComparison.compare(same, 100, 500, 20);

        assertThat(c.runtimeBeatsPercent()).isZero();
        assertThat(c.memoryBeatsPercent()).isZero();
    }

    @Test
    @DisplayName("The histogram has ten columns, holds everyone, and marks the column this student is in")
    void histogram() {
        var c = CodingComparison.compare(others(30, 100), 200, 1000, 20);

        assertThat(c.runtimeBuckets()).hasSize(10);
        assertThat(c.runtimeBuckets().stream().mapToInt(b -> b.count()).sum()).isEqualTo(31);
        assertThat(c.runtimeBuckets().stream().filter(b -> b.yours())).hasSize(1);
        var mine = c.runtimeBuckets().stream().filter(b -> b.yours()).findFirst().orElseThrow();
        assertThat(200).isBetween(mine.from(), mine.to());
    }

    @Test
    @DisplayName("Students with no memory figure are left out of the memory comparison, not counted as zero")
    void unknownMemory() {
        List<Other> mixed = new ArrayList<>(others(24, 100));
        mixed.addAll(IntStream.range(0, 6).mapToObj(i -> new Other(100 + i, null)).toList());

        var c = CodingComparison.compare(mixed, 150, 1500, 20);

        assertThat(c.available()).isTrue();
        // Only the 24 with a figure count: 1500 beats those above it (1600..3300).
        assertThat(c.memoryBeatsPercent()).isEqualTo(Math.round(100.0 * 18 / 24));
    }

    // ------------------------------------------------------------------ the service around it

    private static CodingRunResponse run(int passed, int total, String compileError, Double time, Integer memory) {
        var cases = List.of(new CodingRunResponse.Case(1, false, passed == total, "1", "1", "1", null, time, memory),
                new CodingRunResponse.Case(2, true, passed == total, null, null, null, null, time, memory));
        return new CodingRunResponse(7L, passed, total, compileError, cases, null);
    }

    @Test
    @DisplayName("Only a run that passed every test is recorded and compared")
    void onlyPassingRunsCount() {
        CodingRunStatRepository repo = mock(CodingRunStatRepository.class);
        var service = new CodingComparisonService(repo);

        var failed = service.attach(run(1, 2, null, 0.05, 2048), 5L);
        var noCompile = service.attach(run(0, 2, "error: ;", 0.05, 2048), 5L);
        var unmeasured = service.attach(run(2, 2, null, null, null), 5L);

        assertThat(failed.comparison()).isNull();
        assertThat(noCompile.comparison()).isNull();
        assertThat(unmeasured.comparison()).isNull();
        verify(repo, never()).keepBest(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("A passing run stores the total time and the largest memory, and leaves this student out of the others")
    void passingRunIsStoredAndCompared() {
        CodingRunStatRepository repo = mock(CodingRunStatRepository.class);
        List<CodingRunStat> existing = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            CodingRunStat s = new CodingRunStat();
            s.setQuestionId(7L);
            s.setStudentId(100L + i);
            s.setRuntimeMs(200 + i * 10);
            s.setMemoryKb(3000);
            existing.add(s);
        }
        CodingRunStat self = new CodingRunStat();
        self.setQuestionId(7L);
        self.setStudentId(5L);
        self.setRuntimeMs(100);
        existing.add(self);
        when(repo.findTop5000ByQuestionId(7L)).thenReturn(existing);
        var service = new CodingComparisonService(repo);

        // Two cases of 50 ms each: 100 ms in total; memory is the larger of the two.
        var response = service.attach(run(2, 2, null, 0.05, 2048), 5L);

        verify(repo).keepBest(eq(7L), eq(5L), eq(100), eq(2048));
        assertThat(response.comparison().available()).isTrue();
        assertThat(response.comparison().sampleSize()).as("25 others plus this student").isEqualTo(26);
        assertThat(response.comparison().runtimeBeatsPercent()).isEqualTo(100);
    }
}
