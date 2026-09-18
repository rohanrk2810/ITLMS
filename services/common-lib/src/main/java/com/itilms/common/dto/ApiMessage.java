package com.itilms.common.dto;

/**
 * Response for operations whose only outcome is "it worked" — publish a course,
 * mark a notification read, revoke a session.
 */
public record ApiMessage(boolean success, String message) {

    public static ApiMessage ok(String message) {
        return new ApiMessage(true, message);
    }
}
