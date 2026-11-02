package com.itilms.admission.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The picture of a student, kept apart so a list of students never loads image bytes. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "student_photos")
public class StudentPhoto {

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] photo;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
