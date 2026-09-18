package com.itilms.common.exception;

import org.springframework.http.HttpStatus;

/** 409 - a unique business key already exists (batch code, email, student code...). */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }

    public static DuplicateResourceException of(String entity, String field, Object value) {
        return new DuplicateResourceException(
                "A %s with %s '%s' already exists".formatted(entity, field, value));
    }
}
