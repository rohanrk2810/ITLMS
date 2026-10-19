package com.itilms.identity.dto.request;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The sidebar items one student must not see, as app paths such as {@code /app/finance}. {@code null} removes the
 * personal setting, so the student follows the institute's menu again; an empty list shows them everything.
 */
public record UserMenuRequest(
        @Size(max = 40) List<@Pattern(regexp = "^/app/[a-z-]{1,40}$", message = "must be an app page path") String> hidden) {
}
