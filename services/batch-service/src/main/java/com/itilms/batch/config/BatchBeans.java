package com.itilms.batch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itilms.common.config.PublicEndpoints;

@Configuration
public class BatchBeans {

    /** The batches the website sells are listed before anyone has an account. */
    @Bean
    PublicEndpoints batchPublicEndpoints() {
        return () -> new String[]{
                "/api/public/batches/**"
        };
    }
}
