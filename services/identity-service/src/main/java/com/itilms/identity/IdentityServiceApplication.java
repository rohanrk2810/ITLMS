package com.itilms.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.itilms.common.entity.JpaAuditingConfig;

/**
 * The only service that can mint a token.
 *
 * <p>Everything else in IT-ILMS verifies tokens; none of them issue one. That
 * asymmetry is deliberate — the signing key exists in exactly one place, so
 * rotating it or moving to asymmetric keys later is a change to one service
 * rather than to twelve.
 *
 * <p>Owns {@code itilms_identity}: accounts, credentials, refresh tokens and
 * password resets. It deliberately does <em>not</em> own student or trainer
 * profiles; that is admission-service's domain, and mixing the two would make
 * this service a bottleneck for every profile edit.
 */
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(JpaAuditingConfig.class)
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
