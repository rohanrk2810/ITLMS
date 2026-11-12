package com.itilms.finance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.BooksLock;
import com.itilms.finance.repository.BooksLockRepository;

import lombok.RequiredArgsConstructor;

/**
 * Closing the books up to a date.
 *
 * <p>After a month or a year is reported and checked, nothing in it may change: a late payment, a bounced cheque or a
 * voided expense dated inside a closed period is refused, so the figures already given to the owner or the accountant
 * stay true. The lock stops at yesterday, so today's work, including online payments, is never blocked.
 */
@Service
@RequiredArgsConstructor
public class BooksLockService {

    private final BooksLockRepository repository;
    private final FinanceProperties props;
    private final EventPublisher events;

    public record Status(LocalDate lockedThrough, String note, Long setBy, Instant setAt) {
    }

    @Transactional(readOnly = true)
    public Status status() {
        BooksLock lock = repository.findById((short) 1).orElseGet(BooksLock::new);
        return new Status(lock.getLockedThrough(), lock.getNote(), lock.getSetBy(), lock.getSetAt());
    }

    /** Refuses a record dated on or before the closed date. */
    @Transactional(readOnly = true)
    public void requireOpen(LocalDate date, String what) {
        LocalDate through = repository.findById((short) 1).map(BooksLock::getLockedThrough).orElse(null);
        if (through != null && date != null && !date.isAfter(through)) {
            throw new BusinessRuleException("The books are closed up to %s, so %s dated %s cannot be changed. "
                    .formatted(through, what, date) + "Ask the administrator to reopen them.");
        }
    }

    /** Closes the books through a date (or clears the lock with null). Moving the date back needs a note. */
    @Transactional
    public Status set(LocalDate through, String note) {
        LocalDate yesterday = LocalDate.now(props.getZone()).minusDays(1);
        if (through != null && through.isAfter(yesterday)) {
            throw new BusinessRuleException("Books can be closed up to yesterday at the latest.");
        }
        BooksLock lock = repository.findById((short) 1).orElseGet(BooksLock::new);
        LocalDate before = lock.getLockedThrough();
        boolean reopening = before != null && (through == null || through.isBefore(before));
        if (reopening && (note == null || note.isBlank())) {
            throw new BusinessRuleException("Say why the books are being reopened.");
        }
        lock.setLockedThrough(through);
        lock.setNote(note == null || note.isBlank() ? null : note.trim());
        lock.setSetBy(SecurityUtils.requirePrincipal().userId());
        lock.setSetAt(Instant.now());
        repository.save(lock);
        events.audit("finance-service", reopening ? "BOOKS_REOPENED" : "BOOKS_CLOSED", "BooksLock", 1L, null,
                Map.of("from", before == null ? "" : before.toString(), "to", through == null ? "" : through.toString(),
                        "note", lock.getNote() == null ? "" : lock.getNote()));
        return status();
    }
}
