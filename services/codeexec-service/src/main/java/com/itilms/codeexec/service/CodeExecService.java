package com.itilms.codeexec.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.dto.LanguageResponse;
import com.itilms.codeexec.dto.RunBatchRequest;
import com.itilms.codeexec.dto.RunCodeRequest;
import com.itilms.codeexec.dto.RunCodeResponse;
import com.itilms.codeexec.dto.RunnerStatusResponse;
import com.itilms.codeexec.exception.RunLimitException;
import com.itilms.common.code.CodeLanguage;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.SecurityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * The rules around a run: who, which language, how big, how often. The running itself is the
 * {@link CodeRunner}'s job.
 */
@Slf4j
@Service
public class CodeExecService {

    private final CodeRunner runner;
    private final CodeExecProperties properties;
    private final RunRateLimiter rateLimiter;
    private final Semaphore inFlight;

    public CodeExecService(CodeRunner runner, CodeExecProperties properties, RunRateLimiter rateLimiter) {
        this.runner = runner;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.inFlight = new Semaphore(properties.getLimits().getMaxConcurrentRuns());
    }

    /** Languages that are switched on, in a fixed order. Says nothing about whether the sandbox is up. */
    public List<LanguageResponse> languages() {
        return Arrays.stream(CodeLanguage.values())
                .filter(this::isEnabled)
                .map(language -> new LanguageResponse(language.name(), language.label()))
                .toList();
    }

    public RunCodeResponse run(RunCodeRequest request) {
        long userId = SecurityUtils.requirePrincipal().userId();
        CodeLanguage language = checkedLanguage(request.language());
        checkSize(request.sourceCode(), request.stdin());

        rateLimiter.acquire(userId);
        acquirePermit();
        try {
            log.info("Run by user {} language {} ({} chars)", userId, language, request.sourceCode().length());
            return toResponse(language, runner.run(language, request.sourceCode(), request.stdin()));
        } finally {
            inFlight.release();
        }
    }

    /**
     * The same program against several inputs, for grading. It counts as ONE run against the caller's
     * rate limit and holds ONE place in the runner, however many inputs there are: a test with eight
     * cases would otherwise use up a student's minute on a single click. A program that does not
     * compile is reported once for every input without being compiled again.
     */
    public List<RunCodeResponse> runBatch(RunBatchRequest request) {
        long userId = SecurityUtils.requirePrincipal().userId();
        CodeLanguage language = checkedLanguage(request.language());
        int maxCases = properties.getLimits().getMaxBatchCases();
        if (request.stdins().size() > maxCases) {
            throw new BusinessRuleException("At most " + maxCases + " inputs can be run at once.");
        }
        request.stdins().forEach(stdin -> checkSize(request.sourceCode(), stdin));

        rateLimiter.acquire(userId);
        acquirePermit();
        try {
            log.info("Batch run by user {} language {} ({} chars, {} inputs)",
                    userId, language, request.sourceCode().length(), request.stdins().size());
            List<RunCodeResponse> responses = new ArrayList<>(request.stdins().size());
            RunCodeResponse compileFailure = null;
            for (String stdin : request.stdins()) {
                if (compileFailure != null) {
                    responses.add(compileFailure);
                    continue;
                }
                RunCodeResponse response = toResponse(language, runner.run(language, request.sourceCode(), stdin));
                if (CodeRunner.Outcome.COMPILE_ERROR.name().equals(response.outcome())) {
                    compileFailure = response;
                }
                responses.add(response);
            }
            return responses;
        } finally {
            inFlight.release();
        }
    }

    private CodeLanguage checkedLanguage(String value) {
        CodeLanguage language = CodeLanguage.parse(value).orElseThrow(() ->
                new BusinessRuleException("Language must be " + CodeLanguage.allowedList()));
        if (!isEnabled(language)) {
            throw new BusinessRuleException("LANGUAGE_NOT_ENABLED", language.label() + " is not enabled on this server.");
        }
        return language;
    }

    private void checkSize(String sourceCode, String stdin) {
        CodeExecProperties.Limits limits = properties.getLimits();
        if (sourceCode.length() > limits.getMaxSourceChars()) {
            throw new BusinessRuleException("Code is limited to " + limits.getMaxSourceChars() + " characters.");
        }
        if (stdin != null && stdin.length() > limits.getMaxStdinChars()) {
            throw new BusinessRuleException("Input is limited to " + limits.getMaxStdinChars() + " characters.");
        }
    }

    /** No queue: a student waiting behind eight others sees a spinner for a minute and clicks again, which makes it worse. */
    private void acquirePermit() {
        if (!inFlight.tryAcquire()) {
            throw new RunLimitException("RUNNER_BUSY", "The code runner is busy. Try again in a few seconds.");
        }
    }

    private RunCodeResponse toResponse(CodeLanguage language, CodeRunner.RunResult result) {
        int max = properties.getLimits().getMaxOutputChars();
        boolean truncated = isLonger(result.stdout(), max) || isLonger(result.stderr(), max)
                || isLonger(result.compileOutput(), max);
        return new RunCodeResponse(
                language.name(),
                result.outcome().name(),
                result.statusDescription(),
                cut(result.stdout(), max),
                cut(result.stderr(), max),
                cut(result.compileOutput(), max),
                result.message(),
                result.timeSeconds(),
                result.memoryKb(),
                truncated);
    }

    public RunnerStatusResponse status() {
        CodeRunner.RunnerStatus status = runner.status();
        return new RunnerStatusResponse(status.configured(), status.reachable(), status.message(),
                status.languages().stream()
                        .map(l -> new RunnerStatusResponse.LanguageStatus(
                                l.language().name(), l.language().label(), l.languageId(), l.mapping(),
                                l.sandboxName(), l.available()))
                        .toList());
    }

    private boolean isEnabled(CodeLanguage language) {
        return runner.enabledLanguages().contains(language);
    }

    private static boolean isLonger(String text, int max) {
        return text != null && text.length() > max;
    }

    private static String cut(String text, int max) {
        return isLonger(text, max) ? text.substring(0, max) : text;
    }
}
