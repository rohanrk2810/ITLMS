package com.itilms.notification.service;

/**
 * What a notification says, trimmed to fit its columns.
 *
 * <p>Trimming here, rather than failing, matters because the content comes
 * from Kafka: an event that can never be stored would be retried and then
 * dropped, and its recipients would hear nothing at all.
 */
public record Content(String type, String title, String message, String actionUrl) {

    public Content {
        type = cut(type == null || type.isBlank() ? "GENERAL" : type.trim(), 40);
        title = cut(title == null || title.isBlank() ? "Notification" : title.trim(), 200);
        actionUrl = actionUrl != null && actionUrl.length() > 500 ? null : actionUrl;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
