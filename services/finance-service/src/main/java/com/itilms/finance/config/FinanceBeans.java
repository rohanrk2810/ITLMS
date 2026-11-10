package com.itilms.finance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itilms.common.config.PublicEndpoints;

@Configuration
public class FinanceBeans {

    /** Razorpay calls this without a token; the request is accepted only with a valid signature. */
    @Bean
    PublicEndpoints financePublicEndpoints() {
        return () -> new String[]{"/api/payments/online/webhook"};
    }
}
