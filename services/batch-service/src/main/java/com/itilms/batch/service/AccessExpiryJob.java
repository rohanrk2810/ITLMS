package com.itilms.batch.service;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Closes the enrolments of students whose access date has passed, once a night after the batch status sweep. Only
 * enrolments with an access date are touched; one without a date has no limit.
 */
@Component
@RequiredArgsConstructor
public class AccessExpiryJob {

    private final BatchValidityService validity;

    @Scheduled(cron = "${itilms.batch.access-sweep-cron:0 20 1 * * *}")
    public void sweep() {
        validity.expireOverdue(LocalDate.now());
    }
}
