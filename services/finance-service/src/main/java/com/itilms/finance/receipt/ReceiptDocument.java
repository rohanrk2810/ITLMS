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
import com.itilms.finance.ledger.PaymentTax;
import com.itilms.finance.ledger.StateCodes;
import com.itilms.finance.repository.PaymentRepository;
import com.itilms.finance.service.GstSettingsService;

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
    private final GstSettingsService gstSettings;
    private final com.itilms.finance.client.AdmissionClient admission;

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
        BatchLookupClient.Batch batch = lookupBatch(plan.getBatchId());
        com.itilms.finance.client.AdmissionClient.StudentSummary student = lookupStudent(plan.getStudentId());
        GstSettingsService.Settings current = gstSettings.current();
        // The rate in force on the day of the payment, so a receipt reprinted later shows what it showed then.
        record Gst(java.math.BigDecimal percent, String gstin, String sacCode) {
        }
        Gst gst = new Gst(GstSettingsService.rateFor(current, payment.getPaymentDate()), current.gstin(), current.sacCode());
        ReceiptPdfRenderer.TaxInfo tax = taxOf(payment, plan, current, student);
        return renderer.render(new ReceiptPdfRenderer.Data(payment.getReceiptNo(), payment.getPaymentDate(),
                plan.getStudentName(), plan.getStudentCode(), course, props.getCurrencySymbol(),
                payment.getAmount(), payment.getMethod().name(), payment.getReferenceNo(), plan.getNetFee(), paidToDate,
                balance, payment.getStatus() == PaymentStatus.REVERSED, institute, logo(institute),
                particulars(payment, plan, course), batchName(batch), props.getBank(),
                gst.percent(), gst.gstin(), gst.sacCode(), student == null ? null : student.phone(), timing(batch),
                props.getReceiptNote(), tax));
    }

    /** The batch, or null when the plan has none or batch-service cannot be reached. */
    BatchLookupClient.Batch lookupBatch(Long batchId) {
        if (batchId == null) {
            return null;
        }
        try {
            return batches.get(batchId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String batchName(BatchLookupClient.Batch batch) {
        if (batch == null) {
            return null;
        }
        return batch.name() != null && !batch.name().isBlank() ? batch.name() : batch.batchCode();
    }

    /** "Mon, Wed, Fri 09:00-11:00", or what is known of it. */
    static String timing(BatchLookupClient.Batch batch) {
        if (batch == null) {
            return null;
        }
        String days = batch.classDays() == null ? "" : batch.classDays().stream()
                .map(d -> d.length() <= 3 ? d : d.substring(0, 1).toUpperCase() + d.substring(1, 3).toLowerCase())
                .collect(java.util.stream.Collectors.joining(", "));
        String time = batch.startTime() == null ? "" : hhmm(batch.startTime()) + (batch.endTime() == null ? "" : "-" + hhmm(batch.endTime()));
        String both = (days + " " + time).trim();
        return both.isEmpty() ? null : both;
    }

    private static String hhmm(String t) {
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    /** The student from admission-service (phone, state), or null when it cannot be reached. */
    private com.itilms.finance.client.AdmissionClient.StudentSummary lookupStudent(Long studentId) {
        try {
            var found = admission.lookup(List.of(studentId));
            return found == null || found.isEmpty() ? null : found.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The GST in this payment, with the rate in force on its date and the fee heads it went towards. */
    ReceiptPdfRenderer.TaxInfo taxOf(Payment payment, FeePlan plan, GstSettingsService.Settings current,
                                     com.itilms.finance.client.AdmissionClient.StudentSummary student) {
        java.math.BigDecimal rate = GstSettingsService.rateFor(current, payment.getPaymentDate());
        String studentState = student == null ? null : student.state();
        boolean inter = PaymentTax.interState(studentState, current.gstin());
        List<PaymentTax.Part> parts;
        try {
            parts = FeeLedger.slicesOf(plan, payments.findByFeePlanIdOrderByPaymentDateAscIdAsc(plan.getId()), payment).stream()
                    .map(sl -> new PaymentTax.Part(sl.amount(), sl.installment().isGstExempt())).toList();
        } catch (RuntimeException e) {
            parts = List.of();
        }
        PaymentTax.Result r = PaymentTax.compute(payment.getAmount(), parts, rate, inter);
        return new ReceiptPdfRenderer.TaxInfo(r.rate(), r.taxable(), r.exempt(), r.cgst(), r.sgst(), r.igst(),
                placeOfSupply(studentState, current.gstin()));
    }

    /** "Maharashtra (27)": the student's state, or the institute's own when the student's is not known. */
    public static String placeOfSupply(String studentState, String gstin) {
        String code = StateCodes.codeOf(studentState);
        if (code != null) {
            return StateCodes.nameOf(code) + " (" + code + ")";
        }
        if (gstin != null && gstin.trim().length() >= 2) {
            String own = gstin.trim().substring(0, 2);
            String name = StateCodes.nameOf(own);
            return name == null ? null : name + " (" + own + ")";
        }
        return null;
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

    byte[] logo(BrandingClient.Branding institute) {
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
