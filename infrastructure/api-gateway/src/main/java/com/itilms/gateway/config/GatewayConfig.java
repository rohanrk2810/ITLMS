package com.itilms.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.itilms.gateway.security.GatewayJwtProperties;

/** Binds {@code itilms.gateway.*} from the config server. */
@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewayConfig {
}
