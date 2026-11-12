package com.itilms.finance.receipt;

import org.springframework.stereotype.Component;

import com.itilms.finance.client.BrandingClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.Payslip;

import lombok.RequiredArgsConstructor;

/** Builds the salary slip PDF for a slip: the institute's letterhead and the slip's figures. */
@Component
@RequiredArgsConstructor
public class PayslipDocument {

    private final PayslipPdfRenderer renderer;
    private final ReceiptDocument receipt;
    private final FinanceProperties props;

    public byte[] pdf(Payslip p) {
        BrandingClient.Branding institute = receipt.institute();
        return renderer.render(new PayslipPdfRenderer.Data(p.getSlipNo(), p.getMonthKey(), p.getStaffName(),
                p.getDesignation(), p.getDaysInMonth(), p.getLwpDays(), props.getCurrencySymbol(), p.getBasic(),
                p.getAllowances(), p.getBonus(), p.getGross(), p.getFixedDeduction(), p.getLwpDeduction(),
                p.getOtherDeduction(), p.totalDeductions(), p.getNet(), p.getNote(),
                p.getStatus() == Payslip.Status.PAID, p.getPaidOn(), p.getPayMethod(), p.getPayReference(),
                institute, receipt.logo(institute)));
    }
}
