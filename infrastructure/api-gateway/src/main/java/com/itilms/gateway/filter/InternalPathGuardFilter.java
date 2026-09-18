package com.itilms.gateway.filter;

import java.nio.charset.StandardCharsets;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Keeps service-to-service endpoints off the public internet.
 *
 * <p>Several services expose small {@code /internal/...} endpoints so their
 * neighbours can answer a narrow question - resolve these course ids, is this
 * student on that register. They are authenticated, but only as "some valid
 * token", because the caller is another service acting on a user's behalf and
 * cannot be held to that user's own role.
 *
 * <p>That is the right trust model inside the cluster and the wrong one at the
 * edge: any logged-in student holds a valid token, so without this filter a
 * student could call a lookup meant for a coordinator's service. Internal
 * traffic goes service to service through Eureka and never through the gateway,
 * so nothing legitimate is lost by refusing these paths here.
 *
 * <p>The reply is 404 rather than 403 on purpose. A 403 would confirm the
 * endpoint exists and invite someone to go looking for a way in.
 */
@Slf4j
@Component
public class InternalPathGuardFilter implements GlobalFilter, Ordered {

    private static final String SEGMENT = "/internal/";
    private static final String SUFFIX = "/internal";

    private static final String BODY = """
            {"status":404,"error":"Not Found","code":"NOT_FOUND",\
            "message":"No handler for this path."}""";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.contains(SEGMENT) || path.endsWith(SUFFIX)) {
            log.warn("Refused external call to internal path {} from {}",
                    path, exchange.getRequest().getRemoteAddress());

            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.NOT_FOUND);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            var buffer = response.bufferFactory().wrap(BODY.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        return chain.filter(exchange);
    }

    /**
     * Ahead of authentication.
     *
     * <p>There is no point validating a token for a request that is going to be
     * refused whatever it says.
     */
    @Override
    public int getOrder() {
        return -150;
    }
}
