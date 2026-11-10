package com.itilms.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Razorpay credentials (config-repo/finance-service.yml, values from deploy/docker/.env). Online payment is off until set. */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.razorpay")
public class RazorpayProperties {

    /** Public id, safe to send to the browser (rzp_test_... or rzp_live_...). */
    private String keyId = "";

    /** Never leaves the server. */
    private String keySecret = "";

    /** The secret set on the webhook in the Razorpay dashboard. */
    private String webhookSecret = "";

    private String baseUrl = "https://api.razorpay.com/v1";

    public boolean enabled() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}
