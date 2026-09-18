package com.itilms.liveclass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itilms.common.config.PublicEndpoints;

/**
 * The one unauthenticated path in this service.
 *
 * <p>LiveKit calls the webhook endpoint machine to machine. It has no IT-ILMS
 * account and carries no bearer token, so the usual filter chain would turn
 * every join and leave notification into a 401 - and attendance for every
 * online class would silently come out empty.
 *
 * <p>Open to the filter chain is not the same as unauthenticated in effect. The
 * endpoint verifies LiveKit's own signature on the request body before it
 * believes a word of it; see {@code LiveKitWebhookController}.
 */
@Configuration
public class LiveClassBeans {

    @Bean
    PublicEndpoints liveClassPublicEndpoints() {
        return () -> new String[]{"/api/liveclass/webhook/**"};
    }
}
