package com.itilms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The only port the outside world talks to.
 *
 * <p>Everything the browser sends arrives here on 8080 and is routed to one of
 * twelve services by path. That gives the system one place to do the things
 * that would otherwise be repeated twelve times and drift: authentication at
 * the edge, CORS, rate limiting, correlation ids, and a single Swagger page.
 *
 * <p>The gateway rejects bad tokens, but it is not the only thing checking them.
 * Each service validates independently — see {@code JwtAuthenticationFilter} in
 * common-lib. Doc S12 puts it plainly: backend permission checks must not rely
 * on something upstream having already looked.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
