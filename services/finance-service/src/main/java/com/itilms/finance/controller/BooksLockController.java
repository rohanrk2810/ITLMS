package com.itilms.finance.controller;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.service.BooksLockService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "Closing the books", description = "No change to a closed period")
@RestController
@RequestMapping("/api/fees/books-lock")
@RequiredArgsConstructor
public class BooksLockController {

    private final BooksLockService service;

    public record LockBody(LocalDate lockedThrough, @Size(max = 255) String note) {
    }

    @Operation(summary = "Up to which date the books are closed")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping
    public BooksLockService.Status status() {
        return service.status();
    }

    @Operation(summary = "Close the books through a date",
            description = "ADMIN only, up to yesterday. Empty date reopens everything; moving the date back needs a note.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping
    public BooksLockService.Status set(@Valid @RequestBody LockBody body) {
        return service.set(body.lockedThrough(), body.note());
    }
}
