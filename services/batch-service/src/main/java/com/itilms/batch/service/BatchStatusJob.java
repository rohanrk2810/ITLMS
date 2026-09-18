package com.itilms.batch.service;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.entity.Batch;
import com.itilms.batch.entity.BatchStatus;
import com.itilms.batch.repository.BatchRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Moves batches through their life cycle on their own dates.
 *
 * <p>A batch whose start date has arrived becomes ONGOING; one past its end date
 * becomes COMPLETED. Left to manual updates, "active batches" on the admin
 * dashboard degrades within a term into "batches somebody once created", and
 * every count built on it becomes untrustworthy.
 *
 * <p>Batches with no end date are left alone: an open-ended batch has not
 * finished, it simply has no planned finish.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchStatusJob {

    private final BatchRepository batchRepository;

    @Scheduled(cron = "${itilms.batch.status-sweep-cron:0 5 1 * * *}")
    @Transactional
    public void sweep() {
        LocalDate today = LocalDate.now();
        var batches = batchRepository.findNeedingStatusTransition(today);
        if (batches.isEmpty()) {
            return;
        }

        int started = 0;
        int completed = 0;

        for (Batch batch : batches) {
            if (batch.getStatus() == BatchStatus.PLANNED && !batch.getStartDate().isAfter(today)) {
                batch.setStatus(BatchStatus.ONGOING);
                started++;
            } else if (batch.getStatus() == BatchStatus.ONGOING
                    && batch.getEndDate() != null && batch.getEndDate().isBefore(today)) {
                batch.setStatus(BatchStatus.COMPLETED);
                completed++;
            }
        }

        batchRepository.saveAll(batches);
        log.info("Batch status sweep: {} started, {} completed", started, completed);
    }
}
