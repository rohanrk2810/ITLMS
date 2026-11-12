package com.itilms.finance.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.entity.Payslip;
import com.itilms.finance.receipt.PayslipDocument;
import com.itilms.finance.service.PayrollService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Tag(name = "Payroll", description = "Staff salaries and salary slips")
@RestController
@RequestMapping("/api/fees/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService service;
    private final PayslipDocument documents;

    public record StructureBody(@NotNull @DecimalMin("0.00") BigDecimal basic,
                                @NotNull @DecimalMin("0.00") BigDecimal allowances,
                                @NotNull @DecimalMin("0.00") BigDecimal fixedDeduction, Boolean active) {
    }

    public record SlipBody(BigDecimal lwpDays, BigDecimal otherDeduction, BigDecimal bonus,
                           @jakarta.validation.constraints.Size(max = 255) String note) {
    }

    public record PayBody(@NotNull String method, @jakarta.validation.constraints.Size(max = 80) String reference,
                          LocalDate paidOn) {
    }

    @Operation(summary = "Staff and their salary structure", description = "ADMIN or FINANCE. Every active staff member, with their pay if set.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/staff")
    public List<PayrollService.StaffRow> staff() {
        return service.staff();
    }

    @Operation(summary = "Set a person's salary structure", description = "ADMIN or FINANCE. Applies to slips made from now on.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PutMapping("/staff/{userId}")
    public PayrollService.StaffRow saveStructure(@PathVariable Long userId, @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid StructureBody body) {
        return service.saveStructure(userId, new PayrollService.StructureRequest(body.basic(), body.allowances(),
                body.fixedDeduction(), body.active()));
    }

    @Operation(summary = "A month's salary slips", description = "ADMIN or FINANCE, with the month's totals.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/months/{month}")
    public PayrollService.MonthView month(@PathVariable String month) {
        return service.month(month);
    }

    @Operation(summary = "Make a month's salary slips", description = "ADMIN or FINANCE. For everyone on payroll without a slip yet.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/months/{month}/generate")
    public Map<String, Integer> generate(@PathVariable String month) {
        return Map.of("created", service.generate(month));
    }

    @Operation(summary = "A year's salary totals", description = "ADMIN or FINANCE.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @GetMapping("/years/{year}")
    public PayrollService.YearView year(@PathVariable int year) {
        return service.year(year);
    }

    @Operation(summary = "Change a slip", description = "ADMIN or FINANCE, before it is approved: leave without pay, other deduction, bonus.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PutMapping("/slips/{id}")
    public PayrollService.SlipView update(@PathVariable Long id, @RequestBody @jakarta.validation.Valid SlipBody body) {
        return service.update(id, new PayrollService.SlipRequest(body.lwpDays(), body.otherDeduction(), body.bonus(), body.note()));
    }

    @Operation(summary = "Approve a month's slips", description = "ADMIN only.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/months/{month}/approve")
    public Map<String, Integer> approveMonth(@PathVariable String month) {
        return Map.of("approved", service.approve(month, null));
    }

    @Operation(summary = "Approve one slip", description = "ADMIN only.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/slips/{id}/approve")
    public Map<String, Integer> approveSlip(@PathVariable Long id) {
        return Map.of("approved", service.approve(null, id));
    }

    @Operation(summary = "Pay a month's approved slips", description = "ADMIN or FINANCE. Records a salary expense for each.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/months/{month}/pay")
    public Map<String, Integer> payMonth(@PathVariable String month, @RequestBody @jakarta.validation.Valid PayBody body) {
        return Map.of("paid", service.pay(month, null, new PayrollService.PayRequest(body.method(), body.reference(), body.paidOn())));
    }

    @Operation(summary = "Pay one approved slip", description = "ADMIN or FINANCE.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PostMapping("/slips/{id}/pay")
    public Map<String, Integer> paySlip(@PathVariable Long id, @RequestBody @jakarta.validation.Valid PayBody body) {
        return Map.of("paid", service.pay(null, id, new PayrollService.PayRequest(body.method(), body.reference(), body.paidOn())));
    }

    @Operation(summary = "My salary slips", description = "Any staff member: their own paid slips.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','TRAINER','PLACEMENT','FINANCE')")
    @GetMapping("/me")
    public List<PayrollService.SlipView> mine() {
        return service.mine();
    }

    @Operation(summary = "A salary slip as a PDF", description = "The finance desk any slip; a staff member only their own paid slip.")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINATOR','TRAINER','PLACEMENT','FINANCE')")
    @GetMapping(value = "/slips/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Payslip slip = service.readable(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Salary-slip-" + slip.getSlipNo() + ".pdf").build().toString())
                .body(documents.pdf(slip));
    }
}
