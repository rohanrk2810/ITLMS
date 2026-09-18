package com.itilms.admission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;

import com.itilms.common.entity.JpaAuditingConfig;

/**
 * Leads, follow-ups, and the student and trainer profiles the rest of the system refers to.
 *
 * <p>Owns the {@code itilms_admission} database and is the only service that
 * connects to it. Anything another service needs from this domain it asks for
 * over HTTP or learns from an event - never by reading these tables.
 */
@EnableScheduling
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(JpaAuditingConfig.class)
public class AdmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdmissionServiceApplication.class, args);
    }
}
