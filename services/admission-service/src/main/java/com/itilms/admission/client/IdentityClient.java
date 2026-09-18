package com.itilms.admission.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Calls into identity-service.
 *
 * <p>Admitting a student needs an account to exist <em>before</em> the profile
 * can reference it, so this one is a synchronous call rather than an event: the
 * caller has to know the new user id, and cannot be handed a student record that
 * points at an account which may or may not appear shortly.
 *
 * <p>The request carries the original user's token (see
 * {@code FeignAuthPropagationConfig} in common-lib), so identity-service applies
 * exactly the permissions the coordinator actually holds.
 */
@FeignClient(name = "identity-service", path = "/api/users",
        fallbackFactory = IdentityClientFallback.class)
public interface IdentityClient {

    /**
     * Creates the login account.
     *
     * <p>The password is left blank so identity-service generates a temporary
     * one and emails it. Admission never sees or transports a credential.
     */
    @PostMapping
    CreatedUser createUser(@RequestBody CreateUserPayload payload);

    @PostMapping("/internal/lookup")
    List<UserSummary> lookup(@RequestBody List<Long> userIds);

    /** Mirrors identity-service's {@code CreateUserRequest}. */
    record CreateUserPayload(
            String firstName,
            String lastName,
            String email,
            String phone,
            String role,
            String password
    ) {
        /** Staff-created account: no password supplied, so one is generated. */
        public static CreateUserPayload withGeneratedPassword(String firstName, String lastName,
                                                              String email, String phone, String role) {
            return new CreateUserPayload(firstName, lastName, email, phone, role, null);
        }
    }

    /**
     * The fields admission actually uses from identity's {@code UserResponse}.
     *
     * <p>Only these are declared. Jackson ignores the rest, so identity-service
     * can add a field without this client needing to be rebuilt — the usual way
     * a shared DTO turns into a distributed deployment problem.
     */
    record CreatedUser(Long id, String email, String phone, String fullName, String role, String status) {
    }

    record UserSummary(Long id, String fullName, String email, String phone, String role, String status,
                       Long profileId) {
    }
}
