package com.itilms.common.dto;

/** Returned by create endpoints so the client can navigate to the new record. */
public record IdResponse(Long id, String reference) {

    public static IdResponse of(Long id) {
        return new IdResponse(id, null);
    }

    public static IdResponse of(Long id, String reference) {
        return new IdResponse(id, reference);
    }
}
