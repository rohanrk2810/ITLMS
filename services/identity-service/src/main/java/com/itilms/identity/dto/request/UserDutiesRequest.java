package com.itilms.identity.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The extra duties a person should have after this change. An empty list withdraws them all. */
public record UserDutiesRequest(

        @NotNull(message = "Send the list of extra duties, empty to withdraw them all")
        @Size(max = 3, message = "At most three extra duties")
        List<String> extraRoles
) {
}
