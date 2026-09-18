package com.itilms.common.exception;

import org.springframework.http.HttpStatus;

/** 404 - the requested record does not exist (or is not visible to this caller). */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public ResourceNotFoundException(String entity, Object id) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", entity + " " + id + " was not found");
    }
}
