package com.itilms.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();

    private String keyFor(MockServerHttpRequest.BaseBuilder<?> request) {
        return config.clientAddressKeyResolver().resolve(MockServerWebExchange.from(request.build())).block();
    }

    @Test
    void anUnresolvedClientAddressDoesNotBreakTheLimiter() {
        // What Spring reports behind a reverse proxy: a host name with no InetAddress. This used to throw.
        var request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(InetSocketAddress.createUnresolved("203.0.113.9", 4000));

        assertThat(keyFor(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void aResolvedAddressIsUsedAsIs() {
        var request = MockServerHttpRequest.post("/api/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 4000));

        assertThat(keyFor(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void theFirstForwardedAddressWinsWhenTheProxySetsIt() {
        var request = MockServerHttpRequest.post("/api/auth/login")
                .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 4000));

        assertThat(keyFor(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void noAddressAtAllIsAKeyNotAnError() {
        assertThat(keyFor(MockServerHttpRequest.post("/api/auth/login"))).isEqualTo("unknown");
    }
}
