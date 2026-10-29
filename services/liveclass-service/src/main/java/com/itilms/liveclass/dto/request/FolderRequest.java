package com.itilms.liveclass.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A folder path such as Java/OOPs/Coding, or, when renaming, the new path in {@code to}. */
public record FolderRequest(
        @NotBlank(message = "Give the folder a name") @Size(max = 300) String path,
        @Size(max = 300) String to) {
}
