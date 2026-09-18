package com.itilms.common.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single error shape every IT-ILMS service returns. Documented once in
 * OpenAPI and parsed once in the React client.
 *
 * @param timestamp    when the failure was produced (UTC)
 * @param status       HTTP status code
 * @param error        HTTP reason phrase
 * @param code         stable machine-readable code the client can branch on
 * @param message      human-readable, safe to display to an end user
 * @param path         request URI
 * @param fieldErrors  per-field messages for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors,
        List<String> details
) {

    public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, path, null, null);
    }

    public static ErrorResponse validation(String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(), "VALIDATION_FAILED",
                "One or more fields are invalid", path, fieldErrors, null);
    }
}
