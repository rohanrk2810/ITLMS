package com.itilms.liveclass.dto.response;

import java.util.List;

/** What an Excel upload did: how many questions were saved, and the rows that were skipped with the reason. */
public record MaterialImportResponse(int imported, List<RowError> errors) {

    public record RowError(int row, String message) {
    }
}
