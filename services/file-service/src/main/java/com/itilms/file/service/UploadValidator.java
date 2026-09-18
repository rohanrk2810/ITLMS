package com.itilms.file.service;

import java.util.Locale;

import org.springframework.util.unit.DataSize;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.file.config.FileProperties;
import com.itilms.file.entity.FileCategory;

/**
 * Doc S12: "validate uploads; restrict MIME type and size". An allow-list,
 * not a block-list - {@code config-repo/file-service.yml} explains why - plus
 * a size ceiling that depends on what the file is for.
 */
public final class UploadValidator {

    private UploadValidator() {
    }

    public static void validate(FileProperties props, String contentType, long size, FileCategory category) {
        if (size <= 0) {
            throw new BusinessRuleException("EMPTY_FILE", "The uploaded file is empty.");
        }
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (props.getAllowedContentTypes().stream().noneMatch(normalized::equals)) {
            throw new BusinessRuleException("UNSUPPORTED_FILE_TYPE",
                    "Files of type '%s' are not accepted.".formatted(contentType));
        }
        DataSize max = props.getMaxSizeByCategory().get(category);
        if (max != null && size > max.toBytes()) {
            throw new BusinessRuleException("FILE_TOO_LARGE",
                    "%s files may not exceed %s.".formatted(category, max));
        }
    }
}
