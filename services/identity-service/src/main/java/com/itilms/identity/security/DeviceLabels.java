package com.itilms.identity.security;

/** Turns a raw User-Agent into something a person recognises, e.g. "Chrome on Windows". */
public final class DeviceLabels {

    private DeviceLabels() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        // Order matters: Edge and Opera also say "Chrome", Chrome also says "Safari".
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("opr/") || ua.contains("opera") ? "Opera"
                : ua.contains("firefox/") ? "Firefox"
                : ua.contains("chrome/") ? "Chrome"
                : ua.contains("safari/") ? "Safari"
                : "Browser";
        String os = ua.contains("windows") ? "Windows"
                : ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("mac os") ? "macOS"
                : ua.contains("linux") ? "Linux"
                : "unknown OS";
        return browser + " on " + os;
    }
}
