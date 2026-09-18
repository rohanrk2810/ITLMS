package com.itilms.identity.controller;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.ApiMessage;
import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.identity.dto.request.CreateUserRequest;
import com.itilms.identity.dto.request.UpdateStatusRequest;
import com.itilms.identity.dto.request.UpdateUserRequest;
import com.itilms.identity.dto.response.UserResponse;
import com.itilms.identity.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Account administration (Doc S11: {@code /api/users}).
 *
 * <p>Authorization here follows the role matrix in Doc S4.1: ADMIN has full
 * control, COORDINATOR may look but not change, and nobody else reaches this
 * controller at all. A student wanting their own record uses
 * {@code GET /api/auth/me}, which needs no special permission because it can
 * only ever return the caller.
 */
@Tag(name = "Users", description = "Account administration")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "List users",
            description = "Filter by role and status, or search across name, email, phone and profile code.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping
    public PageResponse<UserResponse> list(
            @Parameter(description = "ADMIN, COORDINATOR, TRAINER, STUDENT, PLACEMENT or FINANCE")
            @RequestParam(required = false) String role,
            @Parameter(description = "ACTIVE, INACTIVE or BLOCKED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Free-text search")
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return userService.search(role, status, query, pageable);
    }

    @Operation(summary = "Get one user")
    @PreAuthorize(Roles.STAFF)
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @Operation(summary = "Create a user account",
            description = "Admin only. Leave the password blank to have a temporary one generated "
                    + "and emailed; the holder is then required to change it at first sign-in.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409", description = "Email or phone already registered")
    })
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Update a user's details",
            description = "Name, email and phone only. Role and status have their own endpoints "
                    + "so that a privilege change is never disguised as a profile edit.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Activate, deactivate or block an account",
            description = "Blocking also revokes the user's live sessions. The institute cannot "
                    + "deactivate its last active administrator.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(id, request);
    }

    @Operation(summary = "Reset a user's password",
            description = "Issues a temporary password by email and revokes existing sessions. "
                    + "The password is never returned in this response.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/{id}/reset-password")
    public ApiMessage resetPassword(@PathVariable Long id) {
        userService.resetPasswordAsAdmin(id);
        return ApiMessage.ok("A temporary password has been emailed to the user");
    }

    @Operation(summary = "Active user counts by role", description = "Feeds the admin dashboard KPI cards.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping("/stats/counts")
    public Map<String, Long> counts() {
        return userService.countsByRole();
    }
}
