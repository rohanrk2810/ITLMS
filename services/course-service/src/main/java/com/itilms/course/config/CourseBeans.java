package com.itilms.course.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itilms.common.config.PublicEndpoints;

@Configuration
public class CourseBeans {

    /**
     * The marketing catalog.
     *
     * <p>Doc S8.1 lists the course catalog and course detail pages as public
     * screens: a prospective student browses before they have any reason to
     * create an account. Only the {@code /public} subtree is exposed, and the
     * service filters those queries to published courses in code rather than
     * from a request parameter.
     */
    @Bean
    PublicEndpoints coursePublicEndpoints() {
        return () -> new String[]{
                "/api/courses/public/**"
        };
    }
}
