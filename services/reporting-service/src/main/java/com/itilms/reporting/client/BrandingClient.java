package com.itilms.reporting.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.extern.slf4j.Slf4j;

/** The institute's public branding in identity-service: the name and address printed on a report. */
@FeignClient(name = "identity-service", contextId = "reportingBrandingClient", fallbackFactory = BrandingClient.Fallback.class)
public interface BrandingClient {

    @GetMapping("/api/public/branding")
    Branding branding();

    record Branding(String name, String tagline, String address, String contactPhone, String contactEmail, String website) {
    }

    /** A null answer: the report is printed without a letterhead rather than not at all. */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BrandingClient> {

        @Override
        public BrandingClient create(Throwable cause) {
            return () -> {
                log.warn("identity-service unreachable reading branding", cause);
                return null;
            };
        }
    }
}
