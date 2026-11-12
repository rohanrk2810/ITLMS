package com.itilms.finance.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class PayslipPdfRendererTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static PayslipPdfRenderer.Employee employee() {
        return new PayslipPdfRenderer.Employee("EMP-104", "ABCDE1234F", "State Bank", "000123456789", "PF/PUN/0042",
                "100200300400", null, LocalDate.of(2024, 4, 24), "Training", "Pune");
    }

    @Test
    void drawsEveryHeadTheEmployeeDetailsAndTheSummary() {
        var data = new PayslipPdfRenderer.Data("SAL-202611-0001", "2026-11", "Ravi Kulkarni", "Trainer", 30, n("1.5"), "Rs.",
                List.of(new PayslipPdfRenderer.Row("Base Salary", n("30000.00")), new PayslipPdfRenderer.Row("House Rent Allowance", n("10000.00")),
                        new PayslipPdfRenderer.Row("Shift Allowance", n("2000.00"))),
                List.of(new PayslipPdfRenderer.Row("Provident Fund", n("3600.00")), new PayslipPdfRenderer.Row("Professional Tax", n("200.00")),
                        new PayslipPdfRenderer.Row("TDS", n("1500.00")), new PayslipPdfRenderer.Row("Leave without pay", n("1400.00"))),
                n("42000.00"), n("6700.00"), n("35300.00"), "Diwali advance adjusted", true, LocalDate.of(2026, 12, 1),
                "BANK_TRANSFER", "UTR123456", employee(), null, null);

        byte[] pdf = new PayslipPdfRenderer().render(data);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(text(pdf)).contains("SALARY SLIP", "November 2026", "Ravi Kulkarni", "Trainer", "SAL-202611-0001",
                "EMP-104", "ABCDE1234F", "State Bank", "000123456789", "PF/PUN/0042", "100200300400", "Pune",
                "01/11/2026 To 30/11/2026", "24/04/2024", "28.5", "Base Salary", "House Rent Allowance", "Shift Allowance",
                "Provident Fund", "Professional Tax", "TDS", "Leave without pay", "Total Earnings", "42,000.00",
                "Total Deduction", "6,700.00", "PAY SUMMARY", "Gross Earning", "35,300.00", "bank transfer", "UTR123456",
                "Diwali advance adjusted");
    }

    @Test
    void sayswhenItIsNotPaidYetAndToleratesMissingDetails() {
        var data = new PayslipPdfRenderer.Data("S", "2026-11", "A", "Coordinator", 30, n("0"), "Rs.",
                List.of(new PayslipPdfRenderer.Row("Basic", n("1000.00"))), List.of(), n("1000.00"), n("0.00"), n("1000.00"), null,
                false, null, null, null, new PayslipPdfRenderer.Employee(null, null, null, null, null, null, null, null, null, null),
                null, null);

        assertThat(text(new PayslipPdfRenderer().render(data))).contains("Not paid yet", "Not Eligible");
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
