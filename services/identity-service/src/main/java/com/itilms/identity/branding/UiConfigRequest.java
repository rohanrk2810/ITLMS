package com.itilms.identity.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The portal customization as a JSON document (theme, menus, dashboard, login text, words). */
public record UiConfigRequest(@NotBlank @Size(max = 20000) String config) {
}
