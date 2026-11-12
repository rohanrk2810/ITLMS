package com.itilms.finance.receipt;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.itilms.finance.client.BatchLookupClient;
import com.itilms.finance.client.BrandingClient;
import com.itilms.finance.client.CourseClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentStatus;
import com.itilms.finance.ledger.FeeLedger;
import com.itilms.finance.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds the receipt PDF for a payment: gathers the institute branding (name, address, contact, colour, logo) and the
 * course title, then draws it. Used both for the email sent when a payment is recorded and for the download in the
 * portal, so the two are always the same document. Branding or the course title being unavailable never stops it:
 * the receipt is drawn with what is known.
 */
@Component
@RequiredArgsConstructor
public class ReceiptDocument {

    private final ReceiptPdfRenderer renderer;
    private final BrandingClient branding;
    private final CourseClient courses;
    private final FinanceProperties props;
    private final PaymentRepository payments;
    private final BatchLookupClient batches;

    /** The institute as printed on the receipt, or null when identity-service cannot be reached. */
    public BrandingClient.Branding institute() {
        try {
            return branding.branding();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String courseTitle(Long courseId) {
        try {
            CourseClient.CourseDetail detail = courses.get(courseId);
            if (detail != null && detail.course() != null && detail.course().title() != null) {
                return detail.course().title();
            }
        } catch (RuntimeException e) {
            // fall through to the generic wording
        }
        return "your course fee";
    }

    public byte[] pdf(Payment payment, FeePlan plan, BigDecimal paidToDate, BigDecimal balance) {
        BrandingClient.Branding institute = institute();
        String course = courseTitle(plan.getCourseId());
        return renderer.render(new ReceiptPdfRenderer.Data(payment.getReceiptNo(), payment.getPaymentDate(),
                plan.getStudentName(), plan.getStudentCode(), course, props.getCurrencySymbol(),
                payment.getAmount(), payment.getMethod().name(), payment.getReferenceNo(), plan.getNetFee(), paidToDate,
                balance, payment.getStatus() == PaymentStatus.REVERSED, institute, logo(institute),
                particulars(payment, plan, course), batchName(plan.getBatchId()), props.getBank()));
    }

    /** The batch name, or null when the plan has no batch or batch-service cannot be reached. */
    String batchName(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            BatchLookupClient.Batch batch = batches.get(batchId);
            if (batch == null) {
                return null;
            }
            return batch.name() != null && !batch.name().isBlank() ? batch.name() : batch.batchCode();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * What the payment was for: each installment it went towards, named by its label (a development fee, a book) or as
     * a part of the course fee. Empty when it cannot be worked out, and the receipt then shows one line.
     */
    List<ReceiptPdfRenderer.Particular> particulars(Payment payment, FeePlan plan, String course) {
        try {
            List<FeeLedger.Slice> slices = FeeLedger.slicesOf(plan,
                    payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()), payment);
            boolean single = plan.getInstallments().stream()
                    .filter(i -> i.getLabel() == null || i.getLabel().isBlank()).count() <= 1;
            List<ReceiptPdfRenderer.Particular> lines = new java.util.ArrayList<>();
            BigDecimal placed = BigDecimal.ZERO;
            for (FeeLedger.Slice slice : slices) {
                String label = slice.installment().getLabel();
                if (label == null || label.isBlank()) {
                    label = course + (single ? " - course fee" : " - fee, installment " + slice.installment().getInstallmentNo());
                }
                lines.add(new ReceiptPdfRenderer.Particular(label, slice.amount()));
                placed = placed.add(slice.amount());
            }
            BigDecimal extra = payment.getAmount().subtract(placed);
            if (!lines.isEmpty() && extra.signum() > 0) {
                lines.add(new ReceiptPdfRenderer.Particular("Paid in advance", extra));
            }
            return lines;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private byte[] logo(BrandingClient.Branding institute) {
        if (institute == null || institute.logoUrl() == null) {
            return null;
        }
        try {
            var response = branding.logo();
            return response == null ? null : response.getBody();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
