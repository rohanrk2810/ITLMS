package com.itilms.common.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Browser origins permitted to call the API.
 *
 * <p>The default is the local React dev server only. Production origins are set
 * through the config server; there is deliberately no wildcard default, because
 * a permissive CORS policy shipped by accident is hard to notice and easy to
 * exploit.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:3000");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private boolean allowCredentials = true;
    private Duration maxAge = Duration.ofHours(1);
}
