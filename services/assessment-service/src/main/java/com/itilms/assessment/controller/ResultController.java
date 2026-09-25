   package com.itilms.assessment.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.assessment.dto.response.CompletionResponse;
import com.itilms.assessment.dto.response.MyResultsResponse;
import com.itilms.assessment.dto.response.StudentPerformanceResponse;
import com.itilms.assessment.service.ResultService;
import com.itilms.assessment.service.StudentPerformanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Results (Doc S8.2, S11). */
@Tag(name = "Results", description = "Test and assignment results")
@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;
    private final StudentPerformanceService performanceService;

    @Operation(summary = "My results",
            description = "Best result per test and every marked assignment. Results a trainer is "
                    + "holding back show as pending.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public MyResultsResponse mine() {
        return resultService.myResults();
    }

    @Operation(summary = "Internal: how one student is doing on tests, coding questions and assignments",
            description = "For reporting-service's student progress report. Not reachable through the gateway; "
                    + "reporting-service decides who may see which student. `forStudent` holds back results a "
                    + "trainer has not released.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/internal/students/{studentId}/performance")
    public StudentPerformanceResponse performance(@PathVariable Long studentId,
                                                  @RequestParam(defaultValue = "") List<Long> batchIds,
                                                  @RequestParam(defaultValue = "") List<Long> courseIds,
                                                  @RequestParam(defaultValue = "false") boolean forStudent) {
        return performanceService.performanceOf(studentId, batchIds, courseIds, forStudent, Instant.now());
    }

    @Operation(summary = "Has a student done the assessed work their course requires?",
            description = "The assessment half of the completion rule (Doc S7.3): every mandatory "
                    + "test passed and every mandatory assignment marked. Used by certificate-service. "
                    + "Students may ask only about themselves.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/students/{studentId}/completion")
    public CompletionResponse completion(@PathVariable Long studentId,
                                         @RequestParam Long courseId,
                                         @RequestParam Long batchId) {
        return resultService.completion(studentId, courseId, batchId);
    }
}
