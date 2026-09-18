package com.itilms.certificate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itilms.common.config.PublicEndpoints;

/**
 * Certificate verification is open to anyone (Doc S6.13: "public verification
 * page/API") - an employer checking a candidate has no IT-ILMS account. It
 * still requires the verification code printed on the certificate, and the
 * gateway rate-limits it.
 */
@Configuration
public class CertificateBeans {

    @Bean
    PublicEndpoints certificatePublicEndpoints() {
        return () -> new String[]{"/api/certificates/verify/**"};
    }
}
