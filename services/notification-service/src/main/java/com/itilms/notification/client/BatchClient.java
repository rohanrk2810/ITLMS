package com.itilms.notification.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.extern.slf4j.Slf4j;

/** Called with the trainer's own token, to check which batches they teach. */
@FeignClient(name = "batch-service", fallbackFactory = BatchClient.Fallback.class)
public interface BatchClient {

    @GetMapping("/api/batches/mine")
    List<BatchSummary> myBatches();

    record BatchSummary(Long id, Long courseId) {
    }

    /** Null: the caller treats "could not check" as "no". */
    @Slf4j
    @Component
    class Fallback implements FallbackFactory<BatchClient> {

        @Override
        public BatchClient create(Throwable cause) {
            return () -> {
                log.warn("batch-service unreachable listing a trainer's batches", cause);
                return null;
            };
        }
    }
}
