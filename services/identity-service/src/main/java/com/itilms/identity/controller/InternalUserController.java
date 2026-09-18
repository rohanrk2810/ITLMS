package com.itilms.identity.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.identity.dto.response.UserSummaryResponse;
import com.itilms.identity.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Lookups the other eleven services need.
 *
 * <p>Notification has a list of user ids and needs email addresses; reporting
 * has a batch roster and needs names for a PDF. Each could hold its own copy of
 * every user, but then six services would be wrong in six different ways the
 * moment someone gets married and changes their surname.
 *
 * <p>Still authenticated, and still restricted to the caller's own rights: an
 * "internal" path is not a trusted path. Requests arrive carrying the original
 * user's token (see {@code FeignAuthPropagationConfig}), so a student's session
 * cannot be used to dump the staff directory just because the call happens to
 * originate inside the cluster.
 */
@Tag(name = "Internal", description = "Service-to-service lookups")
@RestController
@RequestMapping("/api/users/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @Operation(summary = "Resolve many user ids at once",
            description = "POST rather than GET because a batch roster can hold hundreds of ids, "
                    + "which would overflow a query string.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/lookup")
    public List<UserSummaryResponse> lookup(@RequestBody List<Long> userIds) {
        return userService.findByIds(userIds);
    }

    @Operation(summary = "All active users holding a role",
            description = "Used to address notifications to, say, every finance user.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/by-role")
    public List<UserSummaryResponse> byRole(@RequestParam String role) {
        return userService.findActiveByRole(role);
    }
}
