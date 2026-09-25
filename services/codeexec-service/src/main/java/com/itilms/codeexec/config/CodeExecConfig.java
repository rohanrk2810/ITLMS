package com.itilms.codeexec.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import com.itilms.codeexec.service.CodeRunner;
import com.itilms.codeexec.service.Judge0CodeRunner;
import com.itilms.codeexec.service.PistonCodeRunner;
import com.itilms.codeexec.service.RunRateLimiter;

@Configuration
@EnableScheduling
public class CodeExecConfig {

    private final RunRateLimiter rateLimiter;

    CodeExecConfig(RunRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Bean
    static Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    static RunRateLimiter runRateLimiter(CodeExecProperties properties, Clock clock) {
        return new RunRateLimiter(properties.getRateLimit(), clock);
    }

    @Bean
    static CodeRunner codeRunner(CodeExecProperties properties) {
        boolean piston = properties.getEngine() == CodeExecProperties.Engine.PISTON;
        String baseUrl = piston ? properties.getPiston().getBaseUrl() : properties.getJudge0().getBaseUrl();
        // The read timeout has to outlast the sandbox's own limits, or we give up on a run the sandbox
        // was about to answer. Both engines hold the connection open until the run ends. Piston also
        // compiles inside the same request (up to its 20s ceiling), so it gets that much extra.
        long extraSeconds = piston ? 25L : 10L;
        RestClient client = restClient(baseUrl, properties.getLimits().getWallTimeSeconds() + extraSeconds);
        return piston ? new PistonCodeRunner(client, properties) : new Judge0CodeRunner(client, properties);
    }

    private static RestClient restClient(String baseUrl, long readTimeoutSeconds) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                // HTTP/1.1 only: the JDK's default h2c upgrade on a POST body is answered 400 by Piston's Node server.
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        RestClient.Builder client = RestClient.builder().requestFactory(factory);
        if (baseUrl != null && !baseUrl.isBlank()) {
            client.baseUrl(baseUrl.strip());
        }
        return client.build();
    }

    /** Hourly is plenty: entries only need to go once their day is over. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    void evictStaleRateLimits() {
        rateLimiter.evictStale();
    }
}
