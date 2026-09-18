package com.itilms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 - the caller is authenticated and holds a role that could perform this
 * kind of action, but not on <em>this</em> record. Ownership checks in
 * {@code SecurityUtils} raise this.
 */
public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
