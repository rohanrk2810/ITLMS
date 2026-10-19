package com.itilms.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/** The pages a signed-out visitor needs must be reachable without a token. */
class GatewayPublicPathsTest {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final GatewayJwtProperties properties = new GatewayJwtProperties();

    private boolean isPublic(String path) {
        return properties.getPublicPaths().stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    @Test
    void theSignInFormCanAskWhetherTheHumanCheckIsOn() {
        assertThat(isPublic("/api/auth/captcha-config")).isTrue();
    }

    @Test
    void theAuthEndpointsAVisitorNeedsStayPublic() {
        assertThat(isPublic("/api/auth/login")).isTrue();
        assertThat(isPublic("/api/auth/forgot-password")).isTrue();
        assertThat(isPublic("/api/public/branding")).isTrue();
    }

    @Test
    void otherAuthEndpointsStillNeedAToken() {
        assertThat(isPublic("/api/auth/me")).isFalse();
        assertThat(isPublic("/api/users")).isFalse();
    }
}
