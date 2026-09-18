package com.itilms.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.itilms.common.entity.JpaAuditingConfig;

/**
 * Stores uploaded bytes and the metadata describing them, and enforces who may download what.
 *
 * <p>Owns the {@code itilms_file} database and is the only service that
 * connects to it. Anything another service needs from this domain it asks for
 * over HTTP or learns from an event - never by reading these tables.
 */
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(JpaAuditingConfig.class)
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
