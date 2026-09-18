package com.itilms.reporting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Doc S15: dashboards and exports, with the guardrail that keeps an export from
 * becoming a denial of service dressed as a feature request.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.reporting")
public class ReportingProperties {

    private int maxExportRows = 50000;

    private int auditRetentionDays = 1095;

    private int dashboardCacheSeconds = 60;
}
