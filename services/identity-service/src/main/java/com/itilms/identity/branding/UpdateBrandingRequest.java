package com.itilms.identity.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBrandingRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 200) String tagline,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must look like #1a2b3c") String primaryColor,
        @Size(max = 160) String contactEmail,
        @Size(max = 30) String contactPhone,
        @Size(max = 200) String website,
        @Size(max = 400) String address,
        @Size(max = 120) String signatoryName,
        @Size(max = 120) String signatoryTitle) {
}
