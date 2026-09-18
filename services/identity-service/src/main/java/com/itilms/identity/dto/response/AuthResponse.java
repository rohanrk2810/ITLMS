package com.itilms.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a successful sign-in returns.
 *
 * <p>The user object travels with the tokens so the React client can render the
 * correct dashboard immediately, without a second round trip to find out who it
 * just signed in. {@code mustChangePassword} is surfaced at the top level
 * because the client has to act on it before showing anything else.
 */
@Schema(description = "Tokens and profile returned on successful authentication")
public record AuthResponse(

        @Schema(description = "Short-lived JWT; send as 'Authorization: Bearer <token>'")
        String accessToken,

        @Schema(description = "Long-lived token used to obtain a new access token")
        String refreshToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "1800")
        long expiresIn,

        boolean mustChangePassword,

        UserResponse user
) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn,
                user.mustChangePassword(), user);
    }
}
