package com.itilms.placement.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.extern.slf4j.Slf4j;

/** Certificates held - how a graduate, no longer in any batch, qualifies for a job. */
@FeignClient(name = "certificate-service", fallbackFactory = CertificateClient.Fallback.class)
public interface CertificateClient {

    @GetMapping("/api/certificates/me")
    List<Certificate> myCertificates();

    record Certificate(Long courseId, Long batchId, String status) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<CertificateClient> {

        @Override
        public CertificateClient create(Throwable cause) {
            return () -> {
                log.warn("certificate-service unreachable listing a student's certificates", cause);
                return null;
            };
        }
    }
}
