package com.itilms.notification.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.extern.slf4j.Slf4j;

/** The institute's public branding, read from identity-service (an anonymous endpoint). */
@FeignClient(name = "identity-service", contextId = "brandingClient", fallbackFactory = BrandingClient.Fallback.class)
public interface BrandingClient {

    @GetMapping("/api/public/branding")
    Branding branding();

    record Branding(String name) {
    }

    /** Null: the caller keeps its configured fallback name. */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BrandingClient> {

        @Override
        public BrandingClient create(Throwable cause) {
            return () -> {
                log.warn("identity-service unreachable reading the institute name", cause);
                return null;
            };
        }
    }
}
