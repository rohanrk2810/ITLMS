package com.itilms.codeexec.service;

import java.util.List;
import java.util.Set;

import com.itilms.common.code.CodeLanguage;

/**
 * Something that can compile and run untrusted code safely.
 *
 * <p>An interface so the sandbox is replaceable - Judge0 today, another engine
 * later - without touching the rules about who may run what. Same shape as
 * file-service's {@code StorageBackend}.
 */
public interface CodeRunner {

    /**
     * Runs the code and reports what happened.
     *
     * @throws com.itilms.codeexec.exception.CodeRunnerUnavailableException when it could not be run at all
     */
    RunResult run(CodeLanguage language, String sourceCode, String stdin);

    /** Is the sandbox there, and does it recognise every language we configured? */
    RunnerStatus status();

    /** Languages this runner has a configuration for. Says nothing about whether the sandbox is up. */
    Set<CodeLanguage> enabledLanguages();

    enum Outcome {
        SUCCESS, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED
    }

    record RunResult(
            Outcome outcome,
            String statusDescription,
            String stdout,
            String stderr,
            String compileOutput,
            String message,
            Double timeSeconds,
            Integer memoryKb
    ) {
    }

    record RunnerStatus(boolean configured, boolean reachable, String message, List<LanguageStatus> languages) {
    }

    /**
     * @param languageId  Judge0's numeric id; null for engines that do not use one
     * @param mapping     what the language is mapped to, e.g. "id 91" or "java 15.0.2"; null when switched off
     * @param sandboxName what the sandbox itself calls it; null when it does not know it
     */
    record LanguageStatus(CodeLanguage language, Integer languageId, String mapping, String sandboxName, boolean available) {
    }
}
