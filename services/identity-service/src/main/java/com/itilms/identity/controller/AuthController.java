package com.itilms.identity.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.ApiMessage;
import com.itilms.common.security.AppPrincipal;
import com.itilms.identity.dto.request.ChangePasswordRequest;
import com.itilms.identity.dto.request.ForgotPasswordRequest;
import com.itilms.identity.dto.request.LoginRequest;
import com.itilms.identity.dto.request.RefreshTokenRequest;
import com.itilms.identity.dto.request.RegisterRequest;
import com.itilms.identity.dto.request.ResetPasswordRequest;
import com.itilms.identity.dto.response.AuthResponse;
import com.itilms.identity.dto.response.UserResponse;
import com.itilms.identity.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Doc Section 11: the {@code /api/auth/*} surface.
 *
 * <p>Most of these are anonymous by necessity — you cannot present a token to
 * the endpoint that gives you one. They are also the endpoints an attacker
 * probes first, which is why the gateway rate-limits this whole path and why the
 * service never confirms whether an address is registered.
 */
@Tag(name = "Authentication", description = "Sign in, tokens and password management")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Sign in",
            description = "Accepts an email address or a phone number. Returns an access token, "
                    + "a refresh token, and the signed-in user's profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "403", description = "Account inactive, blocked, or temporarily locked"),
            @ApiResponse(responseCode = "429", description = "Too many attempts from this address")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, metadata(http));
    }

    @Operation(summary = "Register as a student",
            description = "Public self-registration. Always creates a STUDENT account; "
                    + "staff accounts are created through POST /api/users.")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, metadata(http)));
    }

    @Operation(summary = "Refresh the access token",
            description = "Rotates the refresh token: the one you send is revoked and a new one returned. "
                    + "Presenting an already-used token revokes every session for that user.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), metadata(http));
    }

    @Operation(summary = "Sign out of this session",
            description = "Revokes the supplied refresh token. Always reports success.")
    @SecurityRequirements
    @PostMapping("/logout")
    public ApiMessage logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiMessage.ok("Signed out");
    }

    @Operation(summary = "Sign out everywhere", description = "Revokes every session for the caller.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout-all")
    public ApiMessage logoutEverywhere(@AuthenticationPrincipal AppPrincipal principal) {
        authService.logoutEverywhere(principal.userId());
        return ApiMessage.ok("Signed out of all devices");
    }

    @Operation(summary = "Change your password",
            description = "Requires the current password. All other sessions are revoked on success.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    public ApiMessage changePassword(@AuthenticationPrincipal AppPrincipal principal,
                                     @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
        return ApiMessage.ok("Password changed. Please sign in again on your other devices.");
    }

    @Operation(summary = "Request a password reset link",
            description = "Always returns 200, whether or not the address is registered. "
                    + "This prevents the endpoint being used to discover who has an account.")
    @SecurityRequirements
    @PostMapping("/forgot-password")
    public ApiMessage forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                     HttpServletRequest http) {
        authService.forgotPassword(request, metadata(http));
        return ApiMessage.ok("If that address belongs to an account, a reset link has been sent to it.");
    }

    @Operation(summary = "Set a new password using a reset token")
    @SecurityRequirements
    @PostMapping("/reset-password")
    public ApiMessage resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiMessage.ok("Your password has been reset. You can now sign in.");
    }

    @Operation(summary = "Who am I",
            description = "The signed-in user's profile, used by the client to pick a dashboard.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppPrincipal principal) {
        return authService.currentUser(principal.userId());
    }

    /**
     * The originating address and client, for the session record.
     *
     * <p>{@code X-Forwarded-For} is preferred because in every deployed
     * topology this service sits behind the gateway and, in production, Nginx
     * too — so the socket address is a proxy's, not the user's.
     */
    private AuthService.ClientMetadata metadata(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip;
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            ip = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return new AuthService.ClientMetadata(ip, request.getHeader("User-Agent"));
    }
}
