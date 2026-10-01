package com.itilms.assessment.service;

import java.util.ArrayList;
import java.util.List;

import com.itilms.assessment.dto.response.CodingRunResponse.Bucket;
import com.itilms.assessment.dto.response.CodingRunResponse.Comparison;

/**
 * The arithmetic of "faster than X% of other students": pure, so it can be tested without a database.
 *
 * <p>A percentage over a handful of students means nothing, so below a minimum sample no comparison is produced at
 * all - the caller is told only how many are needed.
 */
public final class CodingComparison {

    static final int BUCKETS = 10;

    private CodingComparison() {
    }

    /** One other student's best figures. {@code memoryKb} may be unknown. */
    public record Other(int runtimeMs, Integer memoryKb) {
    }

    /**
     * @param others     every other student's best figures for this question (not this student)
     * @param minSample  how many students (including this one) must have solved it before anything is shown
     */
    public static Comparison compare(List<Other> others, int runtimeMs, Integer memoryKb, int minSample) {
        int sample = others.size() + 1;
        if (sample < minSample) {
            return new Comparison(false, sample, minSample, null, null, runtimeMs, memoryKb, List.of(), List.of());
        }

        long slower = others.stream().filter(o -> o.runtimeMs() > runtimeMs).count();
        Integer runtimePercent = (int) Math.round(100.0 * slower / others.size());

        Integer memoryPercent = null;
        List<Integer> memories = new ArrayList<>();
        for (Other o : others) {
            if (o.memoryKb() != null) {
                memories.add(o.memoryKb());
            }
        }
        if (memoryKb != null && !memories.isEmpty()) {
            long heavier = memories.stream().filter(m -> m > memoryKb).count();
            memoryPercent = (int) Math.round(100.0 * heavier / memories.size());
        }

        List<Integer> runtimes = new ArrayList<>(others.stream().map(Other::runtimeMs).toList());
        runtimes.add(runtimeMs);
        List<Integer> mems = new ArrayList<>(memories);
        if (memoryKb != null) {
            mems.add(memoryKb);
        }
        return new Comparison(true, sample, minSample, runtimePercent, memoryPercent, runtimeMs, memoryKb,
                histogram(runtimes, runtimeMs), memoryKb == null || mems.isEmpty() ? List.of() : histogram(mems, memoryKb));
    }

    /** Ten equal-width buckets from the smallest to the largest value, marking the one that holds {@code yours}. */
    static List<Bucket> histogram(List<Integer> values, int yours) {
        int min = values.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = values.stream().mapToInt(Integer::intValue).max().orElse(0);
        int width = Math.max(1, (int) Math.ceil((max - min + 1) / (double) BUCKETS));
        int[] counts = new int[BUCKETS];
        for (int v : values) {
            counts[Math.min(BUCKETS - 1, (v - min) / width)]++;
        }
        int mine = Math.min(BUCKETS - 1, (yours - min) / width);
        List<Bucket> buckets = new ArrayList<>(BUCKETS);
        for (int i = 0; i < BUCKETS; i++) {
            buckets.add(new Bucket(min + i * width, min + (i + 1) * width - 1, counts[i], i == mine));
        }
        return buckets;
    }
}
