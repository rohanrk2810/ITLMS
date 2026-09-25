package com.itilms.codeexec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Runs the code a student types into a lesson's practice editor.
 *
 * <p>It never runs anything itself. Untrusted code goes to a sandbox (Judge0,
 * self-hosted) that has its own process, time, memory and network limits; this
 * service decides <em>whether</em> a run may happen (signed in, language
 * enabled, not too big, not too often) and hands the answer back.
 *
 * <p>Stateless by design: no database, no events. A run is a request and a
 * response.
 */
@EnableDiscoveryClient
@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeExecServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeExecServiceApplication.class, args);
    }
}
