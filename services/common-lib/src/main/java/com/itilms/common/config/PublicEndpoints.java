package com.itilms.common.config;

/**
 * How a service declares the endpoints that do not require a token.
 *
 * <p>The shared security chain locks everything down by default. A service that
 * genuinely has anonymous endpoints — the public course catalog, certificate
 * verification, the enquiry form — publishes a bean like this:
 *
 * <pre>{@code
 * @Bean
 * PublicEndpoints certificatePublicPaths() {
 *     return () -> new String[] { "/api/certificates/verify/**" };
 * }
 * }</pre>
 *
 * <p>Declaring it as a bean rather than editing a shared list keeps each
 * service's public surface visible inside that service, where it gets reviewed.
 */
@FunctionalInterface
public interface PublicEndpoints {

    /** Ant-style patterns reachable without authentication. */
    String[] paths();
}
