package com.itilms.assessment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.assessment.dto.response.CompletionResponse;
import com.itilms.assessment.dto.response.MyResultsResponse;
import com.itilms.assessment.service.ResultService;

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

    @Operation(summary = "My results",
            description = "Best result per test and every marked assignment. Results a trainer is "
                    + "holding back show as pending.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me")
    public MyResultsResponse mine() {
        return resultService.myResults();
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
