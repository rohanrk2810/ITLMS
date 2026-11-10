package com.itilms.finance.receipt;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.itilms.finance.client.BrandingClient;
import com.itilms.finance.client.CourseClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;
import com.itilms.finance.entity.PaymentStatus;

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
        return renderer.render(new ReceiptPdfRenderer.Data(payment.getReceiptNo(), payment.getPaymentDate(),
                plan.getStudentName(), plan.getStudentCode(), courseTitle(plan.getCourseId()), props.getCurrencySymbol(),
                payment.getAmount(), payment.getMethod().name(), payment.getReferenceNo(), plan.getNetFee(), paidToDate,
                balance, payment.getStatus() == PaymentStatus.REVERSED, institute, logo(institute)));
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
