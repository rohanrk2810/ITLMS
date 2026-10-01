package com.itilms.codeexec.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.codeexec.analysis.StaticAnalyzer;
import com.itilms.codeexec.dto.AnalysisResponse;
import com.itilms.codeexec.dto.AnalyzeRequest;
import com.itilms.codeexec.dto.LanguageResponse;
import com.itilms.codeexec.dto.RunBatchRequest;
import com.itilms.codeexec.dto.RunCodeRequest;
import com.itilms.codeexec.dto.RunCodeResponse;
import com.itilms.codeexec.dto.RunnerStatusResponse;
import com.itilms.codeexec.service.CodeExecService;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Practice editor back end: run a snippet in a sandbox and read the result. */
@Tag(name = "Code execution", description = "Run practice code in a sandbox")
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeExecController {

    private final CodeExecService codeExecService;

    @Operation(summary = "Languages the editor can run",
            description = "Only languages that are switched on in configuration. Empty until Judge0 is set up.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/languages")
    public List<LanguageResponse> languages() {
        return codeExecService.languages();
    }

    @Operation(summary = "Run code",
            description = "A compile error or a crash in the student's code is a normal 200 answer whose "
                    + "outcome says so. 429 when the caller runs too often or the runner is busy; "
                    + "503 when the sandbox cannot be reached.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/run")
    public RunCodeResponse run(@Valid @RequestBody RunCodeRequest request) {
        return codeExecService.run(request);
    }

    @Operation(summary = "Run code against several inputs",
            description = "For grading. One rate-limit unit however many inputs; answers come back in the order sent.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/run-batch")
    public List<RunCodeResponse> runBatch(@Valid @RequestBody RunBatchRequest request) {
        return codeExecService.runBatch(request);
    }

    @Operation(summary = "Estimate time and space complexity",
            description = "Reads the code without running it, so it needs no sandbox and cannot be used to run anything. "
                    + "The answer is an estimate: it carries its reasons, a confidence level and, where it sees one, "
                    + "a suggestion. SQL is not analysed.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/analyze")
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return StaticAnalyzer.analyze(request.language(), request.sourceCode());
    }

    @Operation(summary = "Sandbox health",
            description = "Is Judge0 configured and reachable, and does it know every language id we mapped? "
                    + "Run this after installing Judge0 or changing language ids.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @GetMapping("/status")
    public RunnerStatusResponse status() {
        return codeExecService.status();
    }
}
