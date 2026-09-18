package com.itilms.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * The single entry point registered in {@code AutoConfiguration.imports}.
 *
 * <p>Adding {@code common-lib} to a service's POM is all it takes to get the
 * shared security chain, the common error contract, Swagger, CORS, event
 * publishing and identity propagation. Nothing to remember, nothing to copy
 * between twelve services — which is the point: consistency you cannot forget
 * to apply.
 *
 * <p>Servlet-only by design. The API gateway is reactive (WebFlux) and does not
 * depend on this library; it validates tokens with its own reactive filter.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        CommonSecurityConfig.class,
        CommonOpenApiConfig.class,
        FeignAuthPropagationConfig.class
})
public class CommonAutoConfiguration {
}
