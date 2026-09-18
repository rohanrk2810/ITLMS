package com.itilms.admission.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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

/** A trainer's professional profile (Doc S6.3). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trainers")
public class Trainer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    /** Free text, e.g. "Java, Spring Boot, Microservices". */
    @Column(length = 200)
    private String specialization;

    @Column(length = 200)
    private String qualification;

    @Column(name = "experience_years", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    private BigDecimal experienceYears = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "joined_on")
    private LocalDate joinedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TrainerStatus status = TrainerStatus.ACTIVE;

    public void syncIdentity(String fullName, String email, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }
}
