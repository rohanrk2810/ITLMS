package com.itilms.codeexec.exception;

import org.springframework.http.HttpStatus;

import com.itilms.common.exception.ApiException;

/**
 * 503 - the sandbox is not set up, cannot be reached, or failed on its own side.
 *
 * <p>Never used for a student's own bug: a compile error or a crash in their code
 * is a normal, successful answer that says so. This is for "we could not run it at all".
 */
public class CodeRunnerUnavailableException extends ApiException {

    public CodeRunnerUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CODE_RUNNER_UNAVAILABLE", message);
    }
}
