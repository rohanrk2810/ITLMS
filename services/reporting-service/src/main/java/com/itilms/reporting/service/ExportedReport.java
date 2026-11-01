package com.itilms.reporting.service;

/** The spreadsheets the browser builds from other services' data; each download is noted in the audit log. */
public enum ExportedReport {
    LEADS,
    ATTENDANCE,
    RESULTS,
    DASHBOARD,
    PROGRESS
}
