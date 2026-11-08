package com.itilms.identity.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The public website's wording as a JSON document (texts in English and Marathi, links, picture ids). */
public record SiteConfigRequest(@NotBlank @Size(max = SiteContentService.MAX_CONFIG_CHARS) String config) {
}
