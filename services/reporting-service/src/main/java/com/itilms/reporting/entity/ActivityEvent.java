package com.itilms.reporting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One dashboard-relevant fact, read back from a handful of other services'
 * events (admissions, enrolments, payments, results, certificates,
 * placements). Doc S15's dashboards are built by grouping this table rather
 * than maintaining counters, so a redelivered event cannot inflate a total -
 * {@code ActivityEventRepository.insertIfAbsent} keys on (event id, metric).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "activity_events")
public class ActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /** What this fact counts toward, e.g. {@code PAYMENT_RECORDED}. */
    @Column(name = "metric_key", nullable = false, length = 60)
    private String metricKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** Set for money metrics (payments); null otherwise. */
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    /** The student, job or other id the fact is about, for future drill-down. */
    @Column(name = "ref_id")
    private Long refId;

    /** A metric-specific breakdown, e.g. a submission's status or a pipeline stage. */
    @Column(length = 60)
    private String dimension;
}
