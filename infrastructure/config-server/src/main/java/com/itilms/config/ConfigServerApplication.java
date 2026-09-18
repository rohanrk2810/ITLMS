package com.itilms.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Serves configuration to every other service.
 *
 * <p>Without this, "change the JWT expiry" or "point at the new database" means
 * editing twelve {@code application.yml} files and rebuilding twelve images.
 * Here it is one file in {@code config-repo/}, and services pick it up at
 * startup — or immediately, via {@code POST /actuator/refresh}.
 *
 * <p>Secrets are never committed. The YAML files reference environment variables
 * ({@code ${DB_PASSWORD}}, {@code ${JWT_SECRET}}) which this server resolves from
 * its own environment, satisfying Doc S12's requirement that credentials come
 * from the environment rather than source control.
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
