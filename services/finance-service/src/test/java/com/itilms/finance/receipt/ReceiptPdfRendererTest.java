package com.itilms.finance.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.itilms.finance.client.BrandingClient;

class ReceiptPdfRendererTest {

    private static ReceiptPdfRenderer.Data data(BrandingClient.Branding institute) {
        return new ReceiptPdfRenderer.Data("RCPT-2026-0007", LocalDate.of(2026, 11, 10), "Asha Patil", "STU-30",
                "Java Full Stack", "Rs.", new BigDecimal("15000.00"), "ONLINE", "pay_Abc123",
                new BigDecimal("15000.00"), new BigDecimal("15000.00"), BigDecimal.ZERO, false, institute, null);
    }

    @Test
    void drawsAPdfWithTheInstituteHeader() {
        var institute = new BrandingClient.Branding("Sprint IT Academy", "#2b0255", "info@x.in", "+91 98000 00000",
                null, "Pune", null);

        byte[] pdf = new ReceiptPdfRenderer().render(data(institute));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1500);
        String text = text(pdf);
        assertThat(text).contains("Sprint IT Academy", "FEE RECEIPT", "RCPT-2026-0007", "Asha Patil", "STU-30",
                "Java Full Stack", "Rs. 15,000.00", "Rupees Fifteen Thousand Only", "Online (Razorpay)", "pay_Abc123",
                "Balance", "Rs. 0.00", "Pune", "Phone: +91 98000 00000", "Email: info@x.in");
        // A sample to look at: target/sample-receipt.pdf
        try {
            java.nio.file.Files.write(java.nio.file.Path.of("target", "sample-receipt.pdf"), pdf);
        } catch (java.io.IOException ignored) {
            // only a convenience
        }
    }

    @Test
    void stillDrawsWhenBrandingCannotBeRead() {
        byte[] pdf = new ReceiptPdfRenderer().render(data(null));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void anInvalidColourFallsBackToTheDefault() {
        var institute = new BrandingClient.Branding("X", "not-a-colour", null, null, null, null, null);

        assertThat(new ReceiptPdfRenderer().render(data(institute))).isNotEmpty();
    }

    @Test
    void writesAmountsInIndianWordsAndGrouping() {
        assertThat(IndianWords.rupees(new BigDecimal("15000"))).isEqualTo("Rupees Fifteen Thousand Only");
        assertThat(IndianWords.rupees(new BigDecimal("125000.50")))
                .isEqualTo("Rupees One Lakh Twenty Five Thousand and Fifty Paise Only");
        assertThat(IndianWords.rupees(new BigDecimal("10000000"))).isEqualTo("Rupees One Crore Only");
        assertThat(IndianWords.rupees(new BigDecimal("101"))).isEqualTo("Rupees One Hundred and One Only");
        assertThat(ReceiptPdfRenderer.money("Rs.", new BigDecimal("1500000"))).isEqualTo("Rs. 15,00,000.00");
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
