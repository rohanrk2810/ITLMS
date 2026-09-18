package com.itilms.admission.entity;

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

/**
 * A student's academic identity, as distinct from their login account.
 *
 * <p>The split matters. {@code users.id} in identity-service answers "can this
 * person sign in"; {@code students.id} answers "whose attendance, fees and
 * certificate is this". Eight other services hold a {@code student_id}, and
 * none of them should have to care whether the account was ever suspended.
 *
 * <p>{@code fullName}, {@code email} and {@code phone} are copies of data
 * identity-service owns. They exist so that listing a batch of forty students
 * is one query rather than forty cross-service lookups. They are refreshed from
 * {@code UserUpdatedEvent}; when the two disagree, identity-service is right.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "students")
public class Student extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The identity-service account. Unique: one account, one student record. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Human-facing code printed on ID cards and read out over the phone. */
    @Column(name = "student_code", nullable = false, length = 30)
    private String studentCode;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "highest_education", length = 160)
    private String highestEducation;

    @Column(length = 160)
    private String college;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 12)
    private String pincode;

    @Column(name = "guardian_name", length = 120)
    private String guardianName;

    @Column(name = "guardian_phone", length = 20)
    private String guardianPhone;

    @Column(name = "emergency_contact", length = 20)
    private String emergencyContact;

    @Column(name = "admission_date", nullable = false)
    @Builder.Default
    private LocalDate admissionDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_source", length = 40)
    private LeadSource admissionSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(columnDefinition = "text")
    private String remarks;

    /** Refreshes the copied identity fields after a UserUpdatedEvent. */
    public void syncIdentity(String fullName, String email, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }
}
