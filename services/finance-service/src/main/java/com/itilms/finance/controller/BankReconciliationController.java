package com.itilms.finance.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.service.BankReconciliationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Tag(name = "Bank reconciliation", description = "Matching the bank statement to the books")
@RestController
@RequestMapping("/api/fees/bank")
@RequiredArgsConstructor
public class BankReconciliationController {

    private final BankReconciliationService service;

    public record ImportBody(@NotEmpty @Valid List<Line> lines) {
    }

    public record Line(@NotNull LocalDate date, String description, String reference, BigDecimal debit, BigDecimal credit) {
    }

    public record MatchBody(@NotBlank String kind, @NotNull Long id) {
    }

    @Operation(summary = "Import bank statement lines", description = "ADMIN or FINANCE. Lines already imported are skipped; clear matches are made at once.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public BankReconciliationService.ImportResult importLines(@Valid @RequestBody ImportBody body) {
        return service.importLines(body.lines().stream().map(l -> new BankReconciliationService.LineInput(l.date(),
                l.description(), l.reference(), l.debit(), l.credit())).toList());
    }

    @Operation(summary = "Statement lines", description = "ADMIN or FINANCE. Newest first, at most 500.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/lines")
    public List<BankReconciliationService.LineView> lines(@RequestParam(defaultValue = "false") boolean unmatched) {
        return service.list(unmatched);
    }

    @Operation(summary = "How the statement and the books stand")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/summary")
    public BankReconciliationService.Summary summary() {
        return service.summary();
    }

    @Operation(summary = "Entries a line could be matched to", description = "Unmatched books entries of the same amount.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/lines/{id}/candidates")
    public List<BankReconciliationService.Entry> candidates(@PathVariable Long id) {
        return service.candidates(id);
    }

    @Operation(summary = "Match a line by hand")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/lines/{id}/match")
    public BankReconciliationService.LineView match(@PathVariable Long id, @Valid @RequestBody MatchBody body) {
        return service.match(id, body.kind(), body.id());
    }

    @Operation(summary = "Undo a match")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/lines/{id}/unmatch")
    public BankReconciliationService.LineView unmatch(@PathVariable Long id) {
        return service.unmatch(id);
    }

    @Operation(summary = "Match everything that has one clear answer")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/auto-match")
    public java.util.Map<String, Integer> autoMatch() {
        return java.util.Map.of("matched", service.autoMatch());
    }
}
