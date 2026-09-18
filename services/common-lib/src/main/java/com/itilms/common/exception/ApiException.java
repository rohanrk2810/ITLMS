package com.itilms.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Base class for failures that map onto a deliberate HTTP response.
 *
 * <p>Anything thrown that is <em>not</em> an {@code ApiException} is treated as a
 * bug by the global handler: it is logged with a stack trace and reported to the
 * caller as a generic 500 with no internal detail leaked.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
