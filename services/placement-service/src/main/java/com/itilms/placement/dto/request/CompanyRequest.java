package com.itilms.placement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "A recruiting company")
public record CompanyRequest(
        @NotBlank(message = "Company name is required") @Size(max = 160) String name,
        @Size(max = 100) String industry,
        @Size(max = 255) String website,
        @Size(max = 160) String location,
        @Size(max = 120) String contactName,
        @Email(message = "Enter a valid email") @Size(max = 160) String contactEmail,
        @Pattern(regexp = "^$|^[0-9+\\- ]{8,20}$", message = "Enter a valid phone number") String contactPhone,
        String notes,
        Boolean active
) {
}
