package com.itilms.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student's best runtime and memory for one coding question. See V5__coding_run_stats.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coding_run_stats")
public class CodingRunStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "runtime_ms", nullable = false)
    private int runtimeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;
}
