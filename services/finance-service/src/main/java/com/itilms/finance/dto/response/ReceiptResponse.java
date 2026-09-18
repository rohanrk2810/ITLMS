package com.itilms.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything a printed receipt carries (Doc S6.12, S15.1).
 *
 * <p>The balance is as of the moment the receipt is fetched, and is labelled
 * so. A reprint months later after another payment shows the student's
 * position now, which is what they will be asking about; the payment itself -
 * amount, date, method, reference - never changes.
 */
@Schema(description = "A payment receipt")
public record ReceiptResponse(
        String receiptNo,
        LocalDate paymentDate,
        String studentCode,
        String studentName,
        Long courseId,
        String currency,
        String currencySymbol,
        BigDecimal amount,
        String method,
        String referenceNo,
        @Schema(description = "SUCCESS, or REVERSED - a reversed receipt is printed marked as cancelled")
        String status,
        BigDecimal netFee,
        BigDecimal paidToDate,
        @Schema(description = "What is still owed as of today")
        BigDecimal balanceAsOfToday
) {
}
