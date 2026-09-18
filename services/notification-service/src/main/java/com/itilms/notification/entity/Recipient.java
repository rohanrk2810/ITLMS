package com.itilms.notification.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Local copy of a user, kept current from identity-service events. Written only by upsert. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "recipients")
public class Recipient {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 160)
    private String email;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(length = 20)
    private String role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
