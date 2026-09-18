package com.itilms.batch.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A cohort taking a course together (Doc S6.6).
 *
 * <p>Course and trainer names are copied in from other services. A timetable
 * screen shows twenty batches at once, and resolving each one's course title and
 * trainer name over HTTP would turn one page load into forty calls. The copies
 * are refreshed from events; the owning service stays authoritative.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "batches")
public class Batch extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. {@code JFS-2026-B03}. Printed on ID cards and used in conversation. */
    @Column(name = "batch_code", nullable = false, length = 30)
    private String batchCode;

    @Column(length = 120)
    private String name;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "course_code", length = 30)
    private String courseCode;

    @Column(name = "course_title", length = 160)
    private String courseTitle;

    @Column(name = "trainer_id")
    private Long trainerId;

    @Column(name = "trainer_user_id")
    private Long trainerUserId;

    @Column(name = "trainer_name", length = 160)
    private String trainerName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Which weekdays the batch meets, as {@code MON,WED,FRI}.
     *
     * <p>A comma-separated string rather than a join table: it is read as a
     * whole, written as a whole, and never queried by individual day. A table
     * would add a join to every batch listing for no benefit.
     */
    @Column(name = "class_days", nullable = false, length = 60)
    @Builder.Default
    private String classDays = "MON,TUE,WED,THU,FRI";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BatchMode mode = BatchMode.OFFLINE;

    @Column(nullable = false)
    @Builder.Default
    private Integer capacity = 30;

    @Column(length = 60)
    private String classroom;

    @Column(name = "meeting_url", length = 600)
    private String meetingUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BatchStatus status = BatchStatus.PLANNED;

    /** The weekdays this batch meets, parsed. */
    public Set<java.time.DayOfWeek> meetingDays() {
        return Arrays.stream(classDays.split(","))
                .map(String::trim)
                .filter(day -> !day.isEmpty())
                .map(Batch::toDayOfWeek)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static java.time.DayOfWeek toDayOfWeek(String token) {
        return switch (token.toUpperCase()) {
            case "MON", "MONDAY" -> java.time.DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> java.time.DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> java.time.DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> java.time.DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> java.time.DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> java.time.DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> java.time.DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    public static final List<String> VALID_DAY_TOKENS =
            List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    /** Session length in minutes, used to judge live-class attendance. */
    public int sessionMinutes() {
        return (int) java.time.Duration.between(startTime, endTime).toMinutes();
    }
}
