package com.itilms.admission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itilms.common.config.PublicEndpoints;

@Configuration
public class AdmissionBeans {

    /**
     * The website enquiry form is the one anonymous write in the system.
     *
     * <p>It has to be: a prospective student has no account and will not create
     * one to ask a question. The gateway rate-limits the path, the request body
     * is deliberately thin, and the lead it creates has no counselor and no
     * status a stranger could influence.
     */
    @Bean
    PublicEndpoints admissionPublicEndpoints() {
        return () -> new String[]{
                "/api/leads/enquiry"
        };
    }
}
