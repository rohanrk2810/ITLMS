package com.itilms.finance.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** The batch a student is in, for the name printed on a receipt. */
@FeignClient(name = "batch-service", contextId = "financeBatchLookup", path = "/api/batches",
        fallbackFactory = BatchLookupClient.Fallback.class)
public interface BatchLookupClient {

    @GetMapping("/{id}")
    Batch get(@PathVariable("id") Long id);

    record Batch(Long id, String batchCode, String name) {
    }

    /** No answer: the receipt is drawn without the batch. */
    @Component
    class Fallback implements FallbackFactory<BatchLookupClient> {
        @Override
        public BatchLookupClient create(Throwable cause) {
            return id -> null;
        }
    }
}
