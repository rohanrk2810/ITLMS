package com.itilms.identity.service;

import com.itilms.identity.dto.request.ChangePasswordRequest;
import com.itilms.identity.dto.request.ForgotPasswordRequest;
import com.itilms.identity.dto.request.LoginRequest;
import com.itilms.identity.dto.request.RegisterRequest;
import com.itilms.identity.dto.request.ResetPasswordRequest;
import com.itilms.identity.dto.response.AuthResponse;
import com.itilms.identity.dto.response.SessionResponse;
import com.itilms.identity.dto.response.UserResponse;

import java.util.List;

/** Everything a user can do with their own credentials. */
public interface AuthService {

    AuthResponse login(LoginRequest request, ClientMetadata metadata);

    AuthResponse register(RegisterRequest request, ClientMetadata metadata);

    /** Exchanges a refresh token for a new pair, rotating the refresh token. */
    AuthResponse refresh(String refreshToken, ClientMetadata metadata);

    /** Ends one session. Safe to call with an unknown token. */
    void logout(String refreshToken);

    /** Ends every session for a user — used on password change and by admins. */
    void logoutEverywhere(Long userId);

    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * Starts a reset. Always appears to succeed: telling an anonymous caller
     * whether an address is registered turns this endpoint into a way to
     * enumerate the institute's users.
     */
    void forgotPassword(ForgotPasswordRequest request, ClientMetadata metadata);

    void resetPassword(ResetPasswordRequest request);

    UserResponse currentUser(Long userId);

    /** Recent login sessions of one user, newest first (login time, last activity, logout, status). */
    List<SessionResponse> sessions(Long userId);

    /**
     * Where the request came from, recorded against sessions and resets so a
     * suspicious sign-in can be traced later.
     */
    record ClientMetadata(String ipAddress, String userAgent) {

        public static ClientMetadata unknown() {
            return new ClientMetadata(null, null);
        }
    }
}
