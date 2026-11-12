package com.itilms.finance.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class StatementPdfRendererTest {

    @Test
    void listsEveryPaymentAndTheBalance() {
        var block = new StatementPdfRenderer.CourseBlock("Java Full Stack", new BigDecimal("30000.00"),
                new BigDecimal("10000.00"), new BigDecimal("20000.00"), List.of(
                new StatementPdfRenderer.PaymentRow(LocalDate.of(2026, 1, 5), "RCPT-2026-000001", "CASH", null,
                        new BigDecimal("10000.00"), false)));
        byte[] pdf = new StatementPdfRenderer().render(new StatementPdfRenderer.Data("Asha Patil", "STU-30", "Rs.",
                LocalDate.of(2026, 2, 1), List.of(block), null, null));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("STATEMENT OF FEES", "Asha Patil", "STU-30", "Java Full Stack", "RCPT-2026-000001",
                "Rs. 20,000.00", "01/02/2026");
    }

    @Test
    void saysSoWhenNothingWasRaised() {
        byte[] pdf = new StatementPdfRenderer().render(new StatementPdfRenderer.Data("Asha", "S", "Rs.",
                LocalDate.of(2026, 2, 1), List.of(), null, null));

        assertThat(text(pdf)).contains("No fees have been raised");
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
