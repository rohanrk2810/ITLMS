package com.itilms.reporting.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.reporting.entity.ActivityEvent;

@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    /** Idempotent: keyed on (event id, metric) so a redelivered event cannot inflate a dashboard total. */
    @Modifying
    @Query(value = """
            INSERT INTO activity_events (event_id, metric_key, occurred_at, event_date, amount, ref_id, dimension)
            VALUES (:eventId, :metricKey, :occurredAt, :eventDate, :amount, :refId, :dimension)
            ON CONFLICT (event_id, metric_key) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("eventId") String eventId, @Param("metricKey") String metricKey,
                        @Param("occurredAt") Instant occurredAt, @Param("eventDate") LocalDate eventDate,
                        @Param("amount") BigDecimal amount, @Param("refId") Long refId,
                        @Param("dimension") String dimension);

    long countByMetricKey(String metricKey);

    long countByMetricKeyAndEventDateGreaterThanEqual(String metricKey, LocalDate since);

    long countByMetricKeyAndDimension(String metricKey, String dimension);

    @Query("select coalesce(sum(a.amount), 0) from ActivityEvent a where a.metricKey = :metricKey")
    BigDecimal sumAmountByMetricKey(@Param("metricKey") String metricKey);

    @Query("select coalesce(sum(a.amount), 0) from ActivityEvent a "
            + "where a.metricKey = :metricKey and a.eventDate >= :since")
    BigDecimal sumAmountByMetricKeySince(@Param("metricKey") String metricKey, @Param("since") LocalDate since);
}
