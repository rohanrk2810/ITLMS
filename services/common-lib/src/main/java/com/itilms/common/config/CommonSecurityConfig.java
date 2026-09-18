package com.itilms.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.itilms.common.security.JwtAuthenticationFilter;
import com.itilms.common.security.JwtProperties;
import com.itilms.common.security.RestAccessDeniedHandler;
import com.itilms.common.security.RestAuthenticationEntryPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * The security chain every business service runs.
 *
 * <p>Shape of the policy:
 * <ul>
 *   <li><b>Deny by default.</b> Anything not explicitly permitted needs a valid
 *       token. Adding a controller does not accidentally expose it.</li>
 *   <li><b>Stateless.</b> No session, no CSRF token — the client authenticates
 *       every request with a bearer token, which is what makes the services
 *       horizontally scalable (Doc S13, Scalability).</li>
 *   <li><b>Role checks live on the methods.</b> This chain answers "is this
 *       caller authenticated?"; {@code @PreAuthorize} on the controller answers
 *       "may this role do this?"; the service layer answers "…to this record?".</li>
 * </ul>
 *
 * <p>In production every request arrives through the gateway, which has already
 * validated the token. The services validate it a second time anyway: a service
 * that trusts its network position is one misconfigured ingress away from being
 * wide open.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ComponentScan(basePackages = "com.itilms.common",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.itilms.common.entity.JpaAuditingConfig.class))
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class CommonSecurityConfig {

    /** Always anonymous: health probes and the service's own API documentation. */
    private static final String[] INFRASTRUCTURE_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler,
                                            List<PublicEndpoints> publicEndpoints,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {

        List<String> permitted = new ArrayList<>(List.of(INFRASTRUCTURE_PATHS));
        publicEndpoints.forEach(provider -> permitted.addAll(List.of(provider.paths())));
        log.info("Security chain: {} anonymous path pattern(s) configured", permitted.size());

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(permitted.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(opts -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)));

        return http.build();
    }

    /**
     * CORS is normally the gateway's job. Services keep a configurable policy so
     * a developer can hit a service directly on its own port without disabling
     * security, and so a misconfigured origin list is a config change rather
     * than a code change (Doc S12: "apply secure CORS rules").
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.getAllowedOrigins());
        configuration.setAllowedMethods(properties.getAllowedMethods());
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition", "X-Total-Count"));
        configuration.setAllowCredentials(properties.isAllowCredentials());
        configuration.setMaxAge(properties.getMaxAge().toSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
