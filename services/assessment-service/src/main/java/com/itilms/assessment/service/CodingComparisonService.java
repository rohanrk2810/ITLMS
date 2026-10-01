package com.itilms.assessment.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.assessment.dto.response.CodingRunResponse;
import com.itilms.assessment.repository.CodingRunStatRepository;

import lombok.RequiredArgsConstructor;

/**
 * Remembers how fast and how light each student's passing solution was, and tells a student how that compares with
 * the others who solved the same question. Only a run that passed every test counts: a program that fails fast is
 * not a fast solution.
 */
@Service
@RequiredArgsConstructor
public class CodingComparisonService {

    private final CodingRunStatRepository stats;

    /** Students who must have solved a question before a comparison is shown. */
    @Value("${itilms.assessment.comparison-min-sample:20}")
    private int minSample = 20;

    /** Adds the comparison to a test-run response when the run passed everything; otherwise returns it unchanged. */
    @Transactional
    public CodingRunResponse attach(CodingRunResponse response, Long studentId) {
        if (studentId == null || response.compileError() != null || response.total() == 0
                || response.passed() != response.total()) {
            return response;
        }
        long totalMs = 0;
        boolean measured = false;
        Integer maxMemory = null;
        for (var c : response.cases()) {
            if (c.timeSeconds() != null) {
                totalMs += Math.round(c.timeSeconds() * 1000);
                measured = true;
            }
            if (c.memoryKb() != null) {
                maxMemory = maxMemory == null ? c.memoryKb() : Math.max(maxMemory, c.memoryKb());
            }
        }
        if (!measured) {
            return response;
        }
        int runtimeMs = (int) Math.min(Integer.MAX_VALUE, totalMs);

        stats.keepBest(response.questionId(), studentId, runtimeMs, maxMemory);
        List<CodingComparison.Other> others = stats.findTop5000ByQuestionId(response.questionId()).stream()
                .filter(s -> !s.getStudentId().equals(studentId))
                .map(s -> new CodingComparison.Other(s.getRuntimeMs(), s.getMemoryKb()))
                .toList();
        return response.withComparison(CodingComparison.compare(others, runtimeMs, maxMemory, minSample));
    }
}
