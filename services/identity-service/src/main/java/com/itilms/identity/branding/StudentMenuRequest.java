package com.itilms.identity.branding;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The sidebar items students must not see, as app paths such as {@code /app/finance}. The dashboard cannot be hidden. */
public record StudentMenuRequest(
        @NotNull @Size(max = 40) List<@Pattern(regexp = "^/app/[a-z-]{1,40}$", message = "must be an app page path") String> hidden) {
}
