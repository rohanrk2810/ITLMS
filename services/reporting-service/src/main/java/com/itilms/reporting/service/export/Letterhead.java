package com.itilms.reporting.service.export;

import java.util.List;

/** What a printed or Excel report says at the top: the institute's own name, tagline, address and contact line. */
public record Letterhead(String name, String tagline, List<String> lines) {

    public static final Letterhead NONE = new Letterhead(null, null, List.of());

    public boolean isEmpty() {
        return name == null || name.isBlank();
    }
}
