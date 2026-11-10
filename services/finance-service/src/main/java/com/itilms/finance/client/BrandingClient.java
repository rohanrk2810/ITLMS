package com.itilms.finance.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

/** The institute name, colour, contact details and logo printed on a receipt (public, no sign-in needed). */
@FeignClient(name = "identity-service", contextId = "financeBranding", fallbackFactory = BrandingClient.Fallback.class)
public interface BrandingClient {

    @GetMapping("/api/public/branding")
    Branding branding();

    @GetMapping("/api/public/branding/logo")
    ResponseEntity<byte[]> logo();

    record Branding(String name, String primaryColor, String contactEmail, String contactPhone, String website,
                    String address, String logoUrl) {
    }

    /** No answer: the receipt is still produced, with a plain header. */
    @Component
    class Fallback implements FallbackFactory<BrandingClient> {
        @Override
        public BrandingClient create(Throwable cause) {
            return new BrandingClient() {
                @Override
                public Branding branding() {
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
