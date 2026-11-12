package com.itilms.finance.receipt;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.SecurityUtils;
import com.itilms.finance.client.AdmissionClient;
import com.itilms.finance.client.BrandingClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.Refund;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.ledger.PaymentTax;
import com.itilms.finance.repository.FeePlanRepository;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.repository.RefundRepository;
import com.itilms.finance.service.GstSettingsService;

import lombok.RequiredArgsConstructor;

/** Builds the credit note for an approved or paid refund: the tax of the original receipt, in proportion to what is returned. */
@Component
@RequiredArgsConstructor
public class CreditNoteDocument {

    private final CreditNotePdfRenderer renderer;
    private final ReceiptDocument receipt;
    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final FeePlanRepository plans;
    private final GstSettingsService gstSettings;
    private final AdmissionClient admission;
    private final FinanceProperties props;

    public record Note(String fileName, byte[] bytes) {
    }

    public Note pdf(Long refundId) {
        Refund refund = refunds.findById(refundId).orElseThrow(() -> new ResourceNotFoundException("Refund", refundId));
        SecurityUtils.requireStudentOwnershipOrStaff(refund.getStudentId());
        if (refund.getStatus() != Refund.Status.APPROVED && refund.getStatus() != Refund.Status.PAID) {
            throw new BusinessRuleException("A credit note is made once the refund is approved.");
        }
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow();
        FeePlan plan = plans.findWithInstallmentsById(refund.getFeePlanId()).orElseThrow();

        GstSettingsService.Settings current = gstSettings.current();
        AdmissionClient.StudentSummary student = lookup(plan.getStudentId());
        String state = student == null ? null : student.state();
        List<PaymentTax.Part> parts = FeeLedger.slicesOf(plan, payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()),
                payment).stream().map(s -> new PaymentTax.Part(s.amount(), s.installment().isGstExempt())).toList();
        var tax = PaymentTax.compute(payment.getAmount(), parts, GstSettingsService.rateFor(current, payment.getPaymentDate()),
                PaymentTax.interState(state, current.gstin())).scaled(refund.getAmount(), payment.getAmount());

        BrandingClient.Branding institute = receipt.institute();
        LocalDate date = refund.getDecidedAt() == null ? LocalDate.now(props.getZone())
                : refund.getDecidedAt().atZone(props.getZone()).toLocalDate();
        String no = "CN-%d-%04d".formatted(date.getYear(), refund.getId());
        String status = refund.getStatus() == Refund.Status.PAID ? "Paid out" : "Approved, not paid yet";
        byte[] bytes = renderer.render(new CreditNotePdfRenderer.Data(no, date, payment.getReceiptNo(), payment.getPaymentDate(),
                plan.getStudentName(), plan.getStudentCode(), receipt.courseTitle(plan.getCourseId()), props.getCurrencySymbol(),
                refund.getAmount(), refund.getReason(), status,
                new ReceiptPdfRenderer.TaxInfo(tax.rate(), tax.taxable(), tax.exempt(), tax.cgst(), tax.sgst(), tax.igst(),
                        ReceiptDocument.placeOfSupply(state, current.gstin())),
                current.gstin(), current.sacCode(), institute, receipt.logo(institute)));
        return new Note("Credit-note-" + no + ".pdf", bytes);
    }

    private AdmissionClient.StudentSummary lookup(Long studentId) {
        try {
            var found = admission.lookup(List.of(studentId));
            return found == null || found.isEmpty() ? null : found.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
