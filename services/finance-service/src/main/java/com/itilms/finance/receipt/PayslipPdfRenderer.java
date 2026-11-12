package com.itilms.finance.receipt;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.itilms.finance.client.BrandingClient;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * A salary slip as a one-page A4 PDF, in the same plain boxed layout as the fee receipt: the institute's logo and name,
 * the employee's details and the pay period, every earning beside every deduction, a pay summary with the net pay in
 * words, and how and when it was paid.
 */
@Component
public class PayslipPdfRenderer {

    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color DEFAULT_BRAND = new Color(0xE0, 0x66, 0x1A);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    /** One line of earnings or deductions. */
    public record Row(String label, BigDecimal amount) {
    }

    /** The employee's details as they were on the slip. Any may be empty. */
    public record Employee(String code, String pan, String bankName, String bankAccount, String pfNumber, String uan,
                           String esiNumber, LocalDate hiredOn, String department, String location) {
    }

    public record Data(String slipNo, String month, String staffName, String designation, int daysInMonth,
                       BigDecimal lwpDays, String currencySymbol, List<Row> earnings, List<Row> deductions,
                       BigDecimal gross, BigDecimal totalDeductions, BigDecimal net, String note, boolean paid,
                       LocalDate paidOn, String payMethod, String payReference, Employee employee,
                       BrandingClient.Branding institute, byte[] logo) {
    }

    public byte[] render(Data d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 30, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Color brand = brand(d.institute());
            doc.add(header(d, brand));

            Paragraph title = new Paragraph("SALARY SLIP", font(Font.BOLD, 12, Color.BLACK));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(14);
            doc.add(title);
            Paragraph month = new Paragraph(monthName(d.month()), font(Font.BOLD, 10, Color.BLACK));
            month.setAlignment(Element.ALIGN_CENTER);
            month.setSpacingBefore(6);
            month.setSpacingAfter(8);
            doc.add(month);

            doc.add(boxed(details(d)));
            doc.add(boxed(pay(d)));
            doc.add(boxed(summary(d)));

            Paragraph footer = new Paragraph("This is a computer generated salary slip and needs no signature.",
                    font(Font.BOLD, 9, Color.BLACK));
            footer.setSpacingBefore(10);
            doc.add(footer);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not draw the salary slip", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ blocks

    private PdfPTable header(Data d, Color brand) throws DocumentException {
        PdfPTable band = new PdfPTable(new float[]{1.3f, 6f});
        band.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        if (d.logo() != null) {
            try {
                Image logo = Image.getInstance(d.logo());
                logo.scaleToFit(70, 70);
                logoCell.addElement(new Phrase(new Chunk(logo, 0, 0, true)));
            } catch (Exception ignored) {
                // a slip without a logo rather than no slip
            }
        }
        band.addCell(logoCell);
        PdfPCell text = new PdfPCell();
        text.setBorder(Rectangle.NO_BORDER);
        text.setVerticalAlignment(Element.ALIGN_MIDDLE);
        String name = d.institute() != null && d.institute().name() != null ? d.institute().name() : "Salary slip";
        text.addElement(new Paragraph(name, font(Font.BOLD, 20, brand)));
        if (d.institute() != null && d.institute().address() != null && !d.institute().address().isBlank()) {
            text.addElement(new Paragraph(d.institute().address().trim(), font(Font.NORMAL, 9, MUTED)));
        }
        band.addCell(text);
        return band;
    }

    /** Employee on the left, pay period and bank on the right, then the days. */
    private PdfPTable details(Data d) {
        Employee e = d.employee();
        YearMonth ym = YearMonth.parse(d.month());
        PdfPTable t = new PdfPTable(new float[]{1.5f, 3f, 1.6f, 3f});
        t.setWidthPercentage(100);
        two(t, "Employee Code :", e.code(), "Pay Period :", ym.atDay(1).format(DATE) + " To " + ym.atEndOfMonth().format(DATE));
        two(t, "Employee Name :", d.staffName(), "Hire Date :", e.hiredOn() == null ? null : e.hiredOn().format(DATE));
        two(t, "Designation :", d.designation(), "PAN :", e.pan());
        two(t, "Department :", e.department(), "Bank :", e.bankName());
        two(t, "Location :", e.location(), "Bank A/C No. :", e.bankAccount());
        two(t, "Slip No :", d.slipNo(), "PF Number :", e.pfNumber());
        two(t, "Calendar Days :", String.valueOf(d.daysInMonth()), "PF UAN Number :", e.uan());
        BigDecimal payable = BigDecimal.valueOf(d.daysInMonth()).subtract(d.lwpDays());
        two(t, "LOP Days :", d.lwpDays().stripTrailingZeros().toPlainString(), "ESI Number :", e.esiNumber() == null ? "Not Eligible" : e.esiNumber());
        two(t, "Days Payable :", payable.stripTrailingZeros().toPlainString(), "", "");
        return t;
    }

    /** Every earning beside every deduction, padded so the two columns end together. */
    private PdfPTable pay(Data d) {
        PdfPTable t = new PdfPTable(new float[]{3f, 1.6f, 3f, 1.6f});
        t.setWidthPercentage(100);
        for (String h : new String[]{"EARNINGS", "AMOUNT", "DEDUCTIONS", "AMOUNT"}) {
            PdfPCell c = cell(h, Font.BOLD, h.equals("AMOUNT") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderWidth(0.8f);
            t.addCell(c);
        }
        int rows = Math.max(Math.max(d.earnings().size(), d.deductions().size()), 4);
        for (int i = 0; i < rows; i++) {
            Row earn = i < d.earnings().size() ? d.earnings().get(i) : null;
            Row ded = i < d.deductions().size() ? d.deductions().get(i) : null;
            t.addCell(cell(earn == null ? "" : earn.label(), Font.NORMAL, Element.ALIGN_LEFT));
            t.addCell(cell(earn == null ? "" : money(earn.amount()), Font.NORMAL, Element.ALIGN_RIGHT));
            t.addCell(cell(ded == null ? "" : ded.label(), Font.NORMAL, Element.ALIGN_LEFT));
            t.addCell(cell(ded == null ? "" : money(ded.amount()), Font.NORMAL, Element.ALIGN_RIGHT));
        }
        PdfPCell[] totals = {cell("Total Earnings", Font.BOLD, Element.ALIGN_LEFT), cell(money(d.gross()), Font.BOLD, Element.ALIGN_RIGHT),
                cell("Total Deduction", Font.BOLD, Element.ALIGN_LEFT), cell(money(d.totalDeductions()), Font.BOLD, Element.ALIGN_RIGHT)};
        for (PdfPCell c : totals) {
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(MUTED);
            t.addCell(c);
        }
        return t;
    }

    /** Pay summary: gross earning, gross deduction, net pay; the net in words; how it was paid. */
    private PdfPTable summary(Data d) {
        PdfPTable t = new PdfPTable(new float[]{2f, 7f});
        t.setWidthPercentage(100);
        PdfPCell h = cell("PAY SUMMARY", Font.BOLD, Element.ALIGN_CENTER);
        h.setColspan(2);
        t.addCell(h);
        String s = d.currencySymbol();
        labelled(t, "Gross Earning :", s + " " + money(d.gross()));
        labelled(t, "Gross Deduction :", s + " " + money(d.totalDeductions()));
        labelled(t, "NET PAY :", s + " " + money(d.net()));
        labelled(t, "In words :", IndianWords.rupees(d.net()));
        labelled(t, "Status :", d.paid()
                ? "Paid on " + d.paidOn().format(DATE) + " by " + method(d.payMethod())
                + (d.payReference() == null ? "" : ", REF. NO : " + d.payReference())
                : "Not paid yet");
        if (d.note() != null && !d.note().isBlank()) {
            labelled(t, "Remarks :", d.note());
        }
        return t;
    }

    // ------------------------------------------------------------------ helpers

    private static PdfPTable boxed(PdfPTable inner) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(8);
        PdfPCell wrap = new PdfPCell(inner);
        wrap.setBorderColor(MUTED);
        wrap.setBorderWidth(0.8f);
        wrap.setPadding(4);
        box.addCell(wrap);
        return box;
    }

    private void two(PdfPTable t, String l1, String v1, String l2, String v2) {
        t.addCell(cell(l1, Font.BOLD, Element.ALIGN_LEFT));
        t.addCell(cell(v1 == null ? "" : v1, Font.NORMAL, Element.ALIGN_LEFT));
        t.addCell(cell(l2, Font.BOLD, Element.ALIGN_LEFT));
        t.addCell(cell(v2 == null ? "" : v2, Font.NORMAL, Element.ALIGN_LEFT));
    }

    private void labelled(PdfPTable t, String label, String value) {
        PdfPCell l = cell(label, Font.BOLD, Element.ALIGN_CENTER);
        l.setBorder(Rectangle.TOP);
        l.setBorderColor(MUTED);
        PdfPCell v = cell(value, Font.BOLD, Element.ALIGN_LEFT);
        v.setBorder(Rectangle.TOP);
        v.setBorderColor(MUTED);
        t.addCell(l);
        t.addCell(v);
    }

    private static PdfPCell cell(String text, int style, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font(style, 10, Color.BLACK)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(3);
        c.setPaddingBottom(3);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static String money(BigDecimal v) {
        return ReceiptPdfRenderer.money("", v).trim();
    }

    /** "2026-11" as "November 2026". */
    static String monthName(String key) {
        YearMonth ym = YearMonth.parse(key);
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
    }

    private static String method(String m) {
        return m == null ? "" : switch (m) {
            case "BANK_TRANSFER" -> "bank transfer";
            default -> m.toLowerCase(Locale.ENGLISH);
        };
    }

    private static Font font(int style, float size, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static Color brand(BrandingClient.Branding b) {
        try {
            return b != null && b.primaryColor() != null && b.primaryColor().matches("#[0-9a-fA-F]{6}")
                    ? Color.decode(b.primaryColor()) : DEFAULT_BRAND;
        } catch (NumberFormatException e) {
            return DEFAULT_BRAND;
        }
    }
}
