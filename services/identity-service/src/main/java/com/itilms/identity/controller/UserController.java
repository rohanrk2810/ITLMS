package com.itilms.identity.controller;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @Operation(summary = "The staff directory",
            description = "Every active user who is not a student. For the finance desk's payroll (ADMIN or FINANCE).")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/staff-directory")
    public java.util.List<com.itilms.identity.dto.response.StaffEntry> staffDirectory() {
        return userService.staffDirectory();
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

    @Operation(summary = "Give or withdraw extra duties",
            description = "Administrator only. A trainer can also be a coordinator, for example. The change applies on the "
                    + "person's very next request. An empty list withdraws every extra duty.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/{id}/duties")
    public UserResponse updateDuties(@PathVariable Long id,
                                     @jakarta.validation.Valid @RequestBody com.itilms.identity.dto.request.UserDutiesRequest request) {
        return userService.updateDuties(id, request.extraRoles());
    }

    @Operation(summary = "Choose which sidebar items one student sees",
            description = "Replaces the institute's student menu for this student. A null list removes the personal "
                    + "setting so they follow the institute's menu again.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/{id}/menu")
    public UserResponse updateMenu(@PathVariable Long id,
                                   @Valid @RequestBody com.itilms.identity.dto.request.UserMenuRequest request) {
        return userService.updateMenu(id, request);
    }

    @Operation(summary = "Delete a user (soft delete)",
            description = "The account can no longer sign in, its sessions end, and its name, e-mail and phone are "
                    + "wiped so they can be used by a new account. Fees, attendance and certificates in other "
                    + "services stay and keep pointing at the same id. You cannot delete yourself or the last "
                    + "active administrator.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @DeleteMapping("/{id}")
    public ApiMessage delete(@PathVariable Long id, @RequestParam String reason) {
        userService.delete(id, reason);
        return ApiMessage.ok("The user has been deleted");
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
