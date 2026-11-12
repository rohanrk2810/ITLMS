package com.itilms.finance.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.service.BooksService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Books", description = "Journal, trial balance, profit and loss, balance sheet")
@RestController
@RequestMapping("/api/fees/books")
@RequiredArgsConstructor
public class BooksController {

    private final BooksService service;

    @Operation(summary = "The books for a period",
            description = "ADMIN or FINANCE. Double-entry journal, trial balance (from the start to the end date), profit and loss for "
                    + "the period, and the balance sheet at its end, all derived from the fee, payment, refund and expense records.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping
    public BooksService.Books books(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.books(from, to);
    }
}
