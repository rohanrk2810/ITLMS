package com.itilms.finance.receipt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.itilms.finance.client.BrandingClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.Payslip;
import com.itilms.finance.entity.PayslipItem;
import com.itilms.finance.repository.PayslipItemRepository;

import lombok.RequiredArgsConstructor;

/** Builds the salary slip PDF for a slip: the institute's letterhead, the employee's details and every head of the pay. */
@Component
@RequiredArgsConstructor
public class PayslipDocument {

    private final PayslipPdfRenderer renderer;
    private final ReceiptDocument receipt;
    private final FinanceProperties props;
    private final PayslipItemRepository items;

    public byte[] pdf(Payslip p) {
        BrandingClient.Branding institute = receipt.institute();
        List<PayslipItem> heads = items.findBySlipIdOrderBySortOrderAscIdAsc(p.getId());
        List<PayslipPdfRenderer.Row> earnings = new ArrayList<>();
        List<PayslipPdfRenderer.Row> deductions = new ArrayList<>();
        for (PayslipItem h : heads) {
            (h.getKind().equals("EARNING") ? earnings : deductions).add(new PayslipPdfRenderer.Row(h.getName(), h.getAmount()));
        }
        if (heads.isEmpty()) {
            // A slip made before heads existed: the three totals it kept.
            earnings.add(new PayslipPdfRenderer.Row("Basic", p.getBasic()));
            if (p.getAllowances().signum() > 0) {
                earnings.add(new PayslipPdfRenderer.Row("Allowances", p.getAllowances()));
            }
            if (p.getFixedDeduction().signum() > 0) {
                deductions.add(new PayslipPdfRenderer.Row("Fixed deduction", p.getFixedDeduction()));
            }
        }
        // What the desk adjusted for this month.
        if (p.getBonus().signum() > 0) {
            earnings.add(new PayslipPdfRenderer.Row("Bonus", p.getBonus()));
        }
        if (p.getLwpDeduction().signum() > 0) {
            deductions.add(new PayslipPdfRenderer.Row("Leave without pay", p.getLwpDeduction()));
        }
        if (p.getOtherDeduction().signum() > 0) {
            deductions.add(new PayslipPdfRenderer.Row("Other deduction", p.getOtherDeduction()));
        }
        BigDecimal deducted = p.totalDeductions();
        PayslipPdfRenderer.Employee employee = new PayslipPdfRenderer.Employee(p.getEmployeeCode(), p.getPan(), p.getBankName(),
                p.getBankAccount(), p.getPfNumber(), p.getUan(), p.getEsiNumber(), p.getHiredOn(), p.getDepartment(), p.getLocation());
        return renderer.render(new PayslipPdfRenderer.Data(p.getSlipNo(), p.getMonthKey(), p.getStaffName(), p.getDesignation(),
                p.getDaysInMonth(), p.getLwpDays(), props.getCurrencySymbol(), earnings, deductions, p.getGross(), deducted,
                p.getNet(), p.getNote(), p.getStatus() == Payslip.Status.PAID, p.getPaidOn(), p.getPayMethod(),
                p.getPayReference(), employee, institute, receipt.logo(institute)));
    }
}
