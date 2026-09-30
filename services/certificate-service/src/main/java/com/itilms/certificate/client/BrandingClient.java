package com.itilms.certificate.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.extern.slf4j.Slf4j;

/** The institute's public branding in identity-service: name, signatory and logo for the printed certificate. */
@FeignClient(name = "identity-service", contextId = "certificateBrandingClient",
        fallbackFactory = BrandingClient.Fallback.class)
public interface BrandingClient {

    @GetMapping("/api/public/branding")
    Branding branding();

    @GetMapping("/api/public/branding/logo")
    ResponseEntity<byte[]> logo();

    record Branding(String name, String signatoryName, String signatoryTitle, String logoUrl, long version) {
    }

    /** Null answers: the caller falls back to the configured name and prints no logo. */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BrandingClient> {

        @Override
        public BrandingClient create(Throwable cause) {
            return new BrandingClient() {
                @Override
                public Branding branding() {
                    log.warn("identity-service unreachable reading branding", cause);
                    return null;
                }

                @Override
                public ResponseEntity<byte[]> logo() {
                    return null;
                }
            };
        }
    }
}
