package com.itilms.finance.receipt;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.itilms.common.event.DomainEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.PaymentReceiptEmailEvent;
import com.itilms.finance.client.BrandingClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.FeePlan;
import com.itilms.finance.entity.Payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hands the receipt for a payment that was just recorded to notification-service to email, PDF attached.
 *
 * <p>This must never get in the way of the payment itself: any failure here (branding unreachable, a logo that cannot
 * be read, Kafka down) is logged and the payment stands. The event is published after the payment commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptEmailer {

    private final ReceiptDocument document;
    private final FinanceProperties props;
    private final EventPublisher events;

    public void send(Payment payment, FeePlan plan, BigDecimal paidToDate, BigDecimal balance) {
        if (plan.getStudentUserId() == null) {
            return;
        }
        try {
            BrandingClient.Branding institute = document.institute();
            String course = document.courseTitle(plan.getCourseId());
            byte[] pdf = document.pdf(payment, plan, paidToDate, balance);

            String who = institute != null && institute.name() != null ? institute.name() : "your institute";
            String message = "Thank you. We received %s %s towards %s. Your receipt (%s) is attached as a PDF."
                    .formatted(props.getCurrencySymbol(), payment.getAmount().toPlainString(), course, payment.getReceiptNo())
                    + (balance.signum() > 0
                    ? " The balance still to pay is %s %s.".formatted(props.getCurrencySymbol(), balance.toPlainString())
                    : " Your fees for this course are fully paid.")
                    + " You can also download it any time from Fees in the portal.";
            events.publishAfterCommit(KafkaTopics.PAYMENT_RECEIPT_EMAIL, String.valueOf(plan.getStudentId()),
                    new PaymentReceiptEmailEvent(DomainEvent.newId(), Instant.now(), plan.getStudentUserId(),
                            payment.getReceiptNo(), "Payment receipt " + payment.getReceiptNo() + " - " + who, message,
                            fileName(payment), pdf));
        } catch (RuntimeException e) {
            log.warn("Could not prepare the receipt email for payment {}", payment.getId(), e);
        }
    }

    static String fileName(Payment payment) {
        return "Receipt-" + payment.getReceiptNo() + ".pdf";
    }
}
