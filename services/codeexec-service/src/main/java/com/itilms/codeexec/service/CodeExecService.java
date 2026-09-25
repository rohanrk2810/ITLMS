package com.itilms.codeexec.service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.dto.LanguageResponse;
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

        CodeLanguage language = CodeLanguage.parse(request.language()).orElseThrow(() ->
                new BusinessRuleException("Language must be " + CodeLanguage.allowedList()));
        if (!isEnabled(language)) {
            throw new BusinessRuleException("LANGUAGE_NOT_ENABLED", language.label() + " is not enabled on this server.");
        }

        CodeExecProperties.Limits limits = properties.getLimits();
        if (request.sourceCode().length() > limits.getMaxSourceChars()) {
            throw new BusinessRuleException("Code is limited to " + limits.getMaxSourceChars() + " characters.");
        }
        if (request.stdin() != null && request.stdin().length() > limits.getMaxStdinChars()) {
            throw new BusinessRuleException("Input is limited to " + limits.getMaxStdinChars() + " characters.");
        }

        rateLimiter.acquire(userId);

        // No queue: a student waiting behind eight others sees a spinner for a minute and clicks
        // again, which makes it worse. Better to say "busy" straight away.
        if (!inFlight.tryAcquire()) {
            throw new RunLimitException("RUNNER_BUSY", "The code runner is busy. Try again in a few seconds.");
        }
        CodeRunner.RunResult result;
        try {
            log.info("Run by user {} language {} ({} chars)", userId, language, request.sourceCode().length());
            result = runner.run(language, request.sourceCode(), request.stdin());
        } finally {
            inFlight.release();
        }

        int max = limits.getMaxOutputChars();
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
