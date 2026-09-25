package com.itilms.assessment.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.itilms.common.exception.BusinessRuleException;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a student's program for grading.
 *
 * <p>Called with the student's own token, which Feign forwards, so codeexec-service applies its usual
 * per-user limits to it. Refusals (busy, over the limit, sandbox down) come back as a
 * {@link BusinessRuleException} with a sentence a student can act on: a coding answer must never be
 * scored zero just because the runner was busy, so callers treat that exception as "not graded yet".
 */
@FeignClient(name = "codeexec-service", fallbackFactory = CodeClient.Fallback.class)
public interface CodeClient {

    @PostMapping("/api/code/run-batch")
    List<RunResult> runBatch(@RequestBody RunBatchRequest request);

    record RunBatchRequest(String language, String sourceCode, List<String> stdins) {
    }

    record RunResult(String language, String outcome, String statusDescription, String stdout, String stderr,
                     String compileOutput, String message, Double timeSeconds, Integer memoryKb,
                     boolean outputTruncated) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<CodeClient> {

        @Override
        public CodeClient create(Throwable cause) {
            return request -> {
                log.warn("Code run for grading failed: {}", cause.toString());
                throw new BusinessRuleException("CODE_RUNNER_UNAVAILABLE", messageFor(cause));
            };
        }

        static String messageFor(Throwable cause) {
            if (cause instanceof FeignException e) {
                if (e.status() == 429) {
                    return "The code runner is busy, or you are running code too often. Wait a few seconds and try again.";
                }
                if (e.status() == 422 || e.status() == 400) {
                    return "This code could not be run. It may be too long, or the language is not switched on.";
                }
            }
            return "The code runner is not available right now. Your code is saved; try again in a moment.";
        }
    }
}
