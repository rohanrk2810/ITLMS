package com.itilms.finance.receipt;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
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
 * A salary slip as a one-page A4 PDF, in the same plain boxed layout as the fee receipt: the institute's logo and
 * name, the person and the month, earnings beside deductions, the net pay with its words, and how it was paid.
 */
@Component
public class PayslipPdfRenderer {

    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color DEFAULT_BRAND = new Color(0xE0, 0x66, 0x1A);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    public record Data(String slipNo, String month, String staffName, String designation, int daysInMonth,
                       BigDecimal lwpDays, String currencySymbol, BigDecimal basic, BigDecimal allowances,
                       BigDecimal bonus, BigDecimal gross, BigDecimal fixedDeduction, BigDecimal lwpDeduction,
                       BigDecimal otherDeduction, BigDecimal totalDeductions, BigDecimal net, String note,
                       boolean paid, LocalDate paidOn, String payMethod, String payReference,
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

            PdfPTable who = new PdfPTable(new float[]{1.4f, 3f, 1.6f, 2.4f});
            who.setWidthPercentage(100);
            info(who, "Slip No :", d.slipNo(), "Month :", monthName(d.month()));
            info(who, "Employee :", d.staffName(), "Designation :", d.designation());
            info(who, "Days in month :", String.valueOf(d.daysInMonth()), "Leave without pay :",
                    d.lwpDays().stripTrailingZeros().toPlainString() + " day(s)");
            doc.add(boxed(who));

            PdfPTable pay = new PdfPTable(new float[]{3f, 1.6f, 3f, 1.6f});
            pay.setWidthPercentage(100);
            for (String h : new String[]{"EARNINGS", "AMOUNT", "DEDUCTIONS", "AMOUNT"}) {
                PdfPCell c = cell(h, Font.BOLD, h.equals("AMOUNT") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderWidth(0.8f);
                pay.addCell(c);
            }
            String s = d.currencySymbol();
            line(pay, "Basic", d.basic(), "Fixed deduction", d.fixedDeduction());
            line(pay, "Allowances", d.allowances(), "Leave without pay", d.lwpDeduction());
            line(pay, "Bonus", d.bonus(), "Other deduction", d.otherDeduction());
            PdfPCell blank = cell("", Font.NORMAL, Element.ALIGN_LEFT);
            blank.setFixedHeight(60);
            blank.setColspan(4);
            pay.addCell(blank);
            for (String[] row : new String[][]{{"Gross earnings", money(d.gross()), "Total deductions", money(d.totalDeductions())}}) {
                PdfPCell a = cell(row[0], Font.BOLD, Element.ALIGN_LEFT);
                PdfPCell b = cell(row[1], Font.BOLD, Element.ALIGN_RIGHT);
                PdfPCell c = cell(row[2], Font.BOLD, Element.ALIGN_LEFT);
                PdfPCell e = cell(row[3], Font.BOLD, Element.ALIGN_RIGHT);
                for (PdfPCell x : new PdfPCell[]{a, b, c, e}) {
                    x.setBorder(Rectangle.TOP);
                    x.setBorderColor(MUTED);
                    pay.addCell(x);
                }
            }
            PdfPTable net = new PdfPTable(new float[]{2f, 7f});
            net.setWidthPercentage(100);
            PdfPCell nl = cell("NET PAY", Font.BOLD, Element.ALIGN_CENTER);
            nl.setBorder(Rectangle.TOP);
            nl.setBorderColor(MUTED);
            PdfPCell nv = cell(s + " " + money(d.net()), Font.BOLD, Element.ALIGN_RIGHT);
            nv.setBorder(Rectangle.TOP);
            nv.setBorderColor(MUTED);
            net.addCell(nl);
            net.addCell(nv);
            labelled(net, "In words :", IndianWords.rupees(d.net()));
            labelled(net, "Status :", d.paid()
                    ? "Paid on " + d.paidOn().format(DATE) + " by " + method(d.payMethod())
                    + (d.payReference() == null ? "" : ", REF. NO : " + d.payReference())
                    : "Not paid yet");
            if (d.note() != null && !d.note().isBlank()) {
                labelled(net, "Remarks :", d.note());
            }
            PdfPTable both = new PdfPTable(1);
            both.setWidthPercentage(100);
            PdfPCell top = new PdfPCell(pay);
            top.setBorder(Rectangle.NO_BORDER);
            both.addCell(top);
            PdfPCell bottom = new PdfPCell(net);
            bottom.setBorder(Rectangle.TOP);
            bottom.setBorderColor(MUTED);
            both.addCell(bottom);
            doc.add(boxed(both));

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

    private void info(PdfPTable t, String l1, String v1, String l2, String v2) {
        t.addCell(cell(l1, Font.BOLD, Element.ALIGN_LEFT));
        t.addCell(cell(v1, Font.NORMAL, Element.ALIGN_LEFT));
        t.addCell(cell(l2, Font.BOLD, Element.ALIGN_LEFT));
        t.addCell(cell(v2, Font.NORMAL, Element.ALIGN_LEFT));
    }

    private void line(PdfPTable t, String l1, BigDecimal a1, String l2, BigDecimal a2) {
        t.addCell(cell(l1, Font.NORMAL, Element.ALIGN_LEFT));
        t.addCell(cell(money(a1), Font.NORMAL, Element.ALIGN_RIGHT));
        t.addCell(cell(l2, Font.NORMAL, Element.ALIGN_LEFT));
        t.addCell(cell(money(a2), Font.NORMAL, Element.ALIGN_RIGHT));
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
        c.setPaddingTop(4);
        c.setPaddingBottom(4);
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
