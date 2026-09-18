package com.itilms.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;

/**
 * Carries the caller's identity across an internal service hop.
 *
 * <p>When batch-service asks admission-service "who is student 42?", that call
 * must still be made <em>as the original caller</em>. Without this, the
 * downstream service sees an anonymous request and either rejects it (breaking
 * the feature) or is given a blanket service account (breaking the audit trail
 * and every per-record ownership rule).
 *
 * <p>Forwarding the original bearer token keeps one identity end to end: the
 * downstream {@code @PreAuthorize} and ownership checks apply exactly as they
 * would on a direct call, and the audit log names a person rather than a robot.
 */
@Configuration
public class FeignAuthPropagationConfig {

    private static final String AUTHORIZATION = "Authorization";
    private static final String CORRELATION_ID = "X-Correlation-Id";

    @Bean
    RequestInterceptor authForwardingInterceptor() {
        return template -> {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                var request = servletAttributes.getRequest();

                String authorization = request.getHeader(AUTHORIZATION);
                if (authorization != null && !template.headers().containsKey(AUTHORIZATION)) {
                    template.header(AUTHORIZATION, authorization);
                }

                String correlationId = request.getHeader(CORRELATION_ID);
                if (correlationId != null && !template.headers().containsKey(CORRELATION_ID)) {
                    template.header(CORRELATION_ID, correlationId);
                }
            }
        };
    }
}
