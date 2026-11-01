package com.itilms.certificate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The picture printed on a card, kept apart so a list of cards never loads image bytes. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "id_card_photos")
public class IdCardPhoto {

    @Id
    @Column(name = "card_id")
    private Long cardId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] photo;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
