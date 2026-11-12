package com.itilms.finance.receipt;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.itilms.finance.client.BrandingClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.dto.response.FeePlanResponse;
import com.itilms.finance.dto.response.PaymentResponse;

import lombok.RequiredArgsConstructor;

/** Builds the statement PDF for a student from their fee plans, the same figures the Fees page shows. */
@Component
@RequiredArgsConstructor
public class StatementDocument {

    private final StatementPdfRenderer renderer;
    private final ReceiptDocument receipt;
    private final FinanceProperties props;

    public byte[] pdf(List<FeePlanResponse> plans) {
        BrandingClient.Branding institute = receipt.institute();
        FeePlanResponse first = plans.isEmpty() ? null : plans.get(0);
        List<StatementPdfRenderer.CourseBlock> blocks = plans.stream()
                .filter(p -> !"CANCELLED".equals(p.status()))
                .map(p -> new StatementPdfRenderer.CourseBlock(receipt.courseTitle(p.courseId()), p.netFee(), p.paid(),
                        p.outstanding(), rows(p)))
                .toList();
        return renderer.render(new StatementPdfRenderer.Data(first == null ? "" : first.studentName(),
                first == null ? "" : first.studentCode(), props.getCurrencySymbol(), LocalDate.now(props.getZone()),
                blocks, institute, receipt.logo(institute)));
    }

    private static List<StatementPdfRenderer.PaymentRow> rows(FeePlanResponse plan) {
        if (plan.payments() == null) {
            return List.of();
        }
        return plan.payments().stream()
                .sorted(java.util.Comparator.comparing(PaymentResponse::paymentDate).thenComparing(PaymentResponse::id))
                .map(x -> new StatementPdfRenderer.PaymentRow(x.paymentDate(), x.receiptNo(), x.method(), x.referenceNo(),
                        x.amount(), "REVERSED".equals(x.status())))
                .toList();
    }
}
