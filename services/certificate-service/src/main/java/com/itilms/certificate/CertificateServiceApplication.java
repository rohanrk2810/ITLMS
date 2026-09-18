package com.itilms.certificate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;

import com.itilms.common.entity.JpaAuditingConfig;

/**
 * Decides when a course is complete, issues the certificate, and answers public verification.
 *
 * <p>Owns the {@code itilms_certificate} database and is the only service that
 * connects to it. Anything another service needs from this domain it asks for
 * over HTTP or learns from an event - never by reading these tables.
 */
@EnableScheduling
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(JpaAuditingConfig.class)
public class CertificateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CertificateServiceApplication.class, args);
    }
}
