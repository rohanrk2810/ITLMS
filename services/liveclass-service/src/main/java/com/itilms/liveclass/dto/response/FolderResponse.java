package com.itilms.liveclass.dto.response;

/** A folder of the caller's question bank and how many questions are filed directly in it. */
public record FolderResponse(String path, int count) {
}
