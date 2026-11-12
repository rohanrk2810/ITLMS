package com.itilms.finance.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The date up to which the books are closed. A single row. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "books_lock")
public class BooksLock {

    @Id
    private Short id = 1;

    @Column(name = "locked_through")
    private LocalDate lockedThrough;

    @Column(length = 255)
    private String note;

    @Column(name = "set_by")
    private Long setBy;

    @Column(name = "set_at")
    private Instant setAt;
}
