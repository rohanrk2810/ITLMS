package com.itilms.gateway.filter;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Stamps every request with an id that travels the whole way down.
 *
 * <p>One student clicking "submit test" can touch the gateway, assessment,
 * course and notification services. When something fails, "which log lines
 * belong to that click?" is the first question, and grep by timestamp is a poor
 * answer on a busy system. The id is generated once here, echoed to the caller,
 * and forwarded by Feign on every internal hop.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String id = correlationId;

        var request = exchange.getRequest().mutate().header(HEADER, id).build();
        exchange.getResponse().getHeaders().set(HEADER, id);

        return chain.filter(exchange.mutate().request(request).build());
    }

    /** First in the chain, so even rejected requests are traceable. */
    @Override
    public int getOrder() {
        return -200;
    }
}
