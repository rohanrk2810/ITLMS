package com.itilms.finance.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PayslipPdfRendererTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void drawsTheSlipWithEarningsDeductionsAndNetPay() {
        var data = new PayslipPdfRenderer.Data("SAL-202611-0001", "2026-11", "Ravi Kulkarni", "Trainer", 30, n("1.5"), "Rs.",
                n("30000.00"), n("5000.00"), n("0.00"), n("35000.00"), n("1000.00"), n("1750.00"), n("0.00"), n("2750.00"),
                n("32250.00"), "Diwali advance adjusted", true, LocalDate.of(2026, 12, 1), "BANK_TRANSFER", "UTR123456",
                null, null);

        byte[] pdf = new PayslipPdfRenderer().render(data);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("SALARY SLIP", "November 2026", "Ravi Kulkarni", "Trainer", "SAL-202611-0001",
                "32,250.00", "Rupees Thirty Two Thousand Two Hundred and Fifty Only", "bank transfer", "UTR123456",
                "Diwali advance adjusted");
    }

    @Test
    void sayswhenItIsNotPaidYet() {
        var data = new PayslipPdfRenderer.Data("S", "2026-11", "A", "Coordinator", 30, n("0"), "Rs.", n("1000.00"),
                n("0.00"), n("0.00"), n("1000.00"), n("0.00"), n("0.00"), n("0.00"), n("0.00"), n("1000.00"), null, false,
                null, null, null, null, null);

        assertThat(text(new PayslipPdfRenderer().render(data))).contains("Not paid yet");
    }

    private static String text(byte[] pdf) {
        try {
            var reader = new com.lowagie.text.pdf.PdfReader(pdf);
            return new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
