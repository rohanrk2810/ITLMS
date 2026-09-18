package com.itilms.placement.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.placement")
public class PlacementProperties {

    /** Application deadlines are dates in the institute's timezone, not the server's. */
    private ZoneId zone = ZoneId.of("Asia/Kolkata");
}
