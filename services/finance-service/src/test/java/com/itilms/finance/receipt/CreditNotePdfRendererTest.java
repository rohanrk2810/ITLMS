package com.itilms.finance.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CreditNotePdfRendererTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static String text(byte[] pdf) {
        try {
            var reader = new com.lowagie.text.pdf.PdfReader(pdf);
            return new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void namesTheReceiptItCreditsAndTheTaxThatGoesBack() {
        var data = new CreditNotePdfRenderer.Data("CN-2026-0004", LocalDate.of(2026, 3, 10), "RCPT-2026-000012",
                LocalDate.of(2026, 3, 1), "Asha Patil", "STU-30", "Java Full Stack", "Rs.", n("5900.00"), "Left the course", "Paid out",
                new ReceiptPdfRenderer.TaxInfo(n("18"), n("5000.00"), BigDecimal.ZERO, n("450.00"), n("450.00"), BigDecimal.ZERO,
                        "Maharashtra (27)"), "27ABCDE1234F1Z5", "999293", null, null);

        byte[] pdf = new CreditNotePdfRenderer().render(data);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(text(pdf)).contains("CREDIT NOTE", "CN-2026-0004", "RCPT-2026-000012", "Asha Patil", "5,900.00",
                "Taxable value", "5,000.00", "CGST @ 9%", "450.00", "SGST @ 9%", "Left the course", "GST No: 27ABCDE1234F1Z5");
    }

    @Test
    void saysSoWhenNoGstWasCharged() {
        var data = new CreditNotePdfRenderer.Data("CN-1", LocalDate.of(2026, 3, 10), "R", LocalDate.of(2026, 3, 1), "A", "S",
                "C", "Rs.", n("100.00"), "x", "Approved, not paid yet", null, "", "", null, null);

        assertThat(text(new CreditNotePdfRenderer().render(data))).contains("No GST was charged on the receipt");
    }
}
