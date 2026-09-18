package com.itilms.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * The service registry.
 *
 * <p>Twelve business services register here on startup and the gateway asks it
 * where to route. Nothing in IT-ILMS hard-codes another service's host and port,
 * which is what allows a service to be restarted, scaled to three instances, or
 * moved to another machine without editing anyone else's configuration.
 *
 * <p>If this process is down, already-running services keep talking to each
 * other from their cached registries — discovery being unavailable slows down
 * topology changes, it does not stop the institute from taking attendance.
 */
@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
