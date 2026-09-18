package com.itilms.gateway.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the client gets when a service is down.
 *
 * <p>A circuit breaker that returns an empty 500 leaves the user staring at a
 * blank screen. Naming the unavailable area — "fees", "live classes" — lets the
 * React client say "Fee information is temporarily unavailable, your other data
 * is fine", which is both more honest and less alarming than a generic error.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/{service}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String service) {
        return build(service);
    }

    @PostMapping("/{service}")
    public ResponseEntity<Map<String, Object>> post(@PathVariable String service) {
        return build(service);
    }

    private ResponseEntity<Map<String, Object>> build(String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 503,
                "error", "Service Unavailable",
                "code", "SERVICE_UNAVAILABLE",
                "service", service,
                "message", "The %s service is temporarily unavailable. Please try again shortly."
                        .formatted(service.replace('-', ' '))));
    }
}
