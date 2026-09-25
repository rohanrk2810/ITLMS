package com.itilms.codeexec.exception;

import org.springframework.http.HttpStatus;

import com.itilms.common.exception.ApiException;

/** 429 - too many runs from this student, or too many in flight for the sandbox. */
public class RunLimitException extends ApiException {

    public RunLimitException(String code, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }
}
