package com.itilms.file.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import com.itilms.file.entity.FileCategory;

/** Builds the path a file is written under: unguessable, and organised for a human browsing the disk. */
public final class StorageKeys {

    private StorageKeys() {
    }

    public static String generate(FileCategory category, String originalFilename) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "%s/%d/%02d/%s-%s".formatted(
                category.name().toLowerCase(Locale.ROOT),
                today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), sanitize(originalFilename));
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.length() > 100 ? safe.substring(safe.length() - 100) : safe;
    }
}
