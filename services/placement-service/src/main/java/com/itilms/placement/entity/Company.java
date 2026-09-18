package com.itilms.placement.entity;

import com.itilms.common.entity.AuditableEntity;

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

/** A recruiter (Doc S6.14). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "companies")
public class Company extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 100)
    private String industry;

    @Column(length = 255)
    private String website;

    @Column(length = 160)
    private String location;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
