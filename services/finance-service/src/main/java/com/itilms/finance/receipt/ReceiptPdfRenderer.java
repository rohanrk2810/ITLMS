package com.itilms.finance.receipt;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * Draws a fee receipt as a one-page A4 PDF: the usual college layout: logo and name, "Student Fees Receipt", a boxed block of
 * receipt, student and course details, a particulars table with the total in words, how it was paid, and where the
 * fees stand. Text uses the standard PDF fonts, which
 * cover Latin script only, so money is shown as "Rs." and not with the rupee sign.
 */
@Component
public class ReceiptPdfRenderer {

    private static final Color DEFAULT_BRAND = new Color(0xE0, 0x66, 0x1A);
    private static final Color INK = new Color(0x22, 0x22, 0x22);
    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color LINE = new Color(0xDD, 0xDD, 0xDD);
    private static final DateTimeFormatter DATE_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    /** One line of the particulars table: what part of the fee this payment went towards. */
    public record Particular(String label, BigDecimal amount) {
    }

    /**
     * Everything on the receipt. The institute and the logo may be null; {@code particulars} may be empty, and the
     * receipt then shows the whole amount as one course-fee line.
     */
    public record Data(String receiptNo, LocalDate date, String studentName, String studentCode,
                       String courseTitle, String currencySymbol, BigDecimal amount, String method, String referenceNo,
                       BigDecimal netFee, BigDecimal paidToDate, BigDecimal balance, boolean reversed,
                       BrandingClient.Branding institute, byte[] logo, java.util.List<Particular> particulars) {
    }

    public byte[] render(Data d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 30, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Watermark(d));
            doc.open();
            Color brand = brand(d.institute());
            doc.add(header(d, brand));

            Paragraph title = new Paragraph(d.reversed() ? "STUDENT FEES RECEIPT - CANCELLED" : "STUDENT FEES RECEIPT",
                    font(Font.BOLD, 12, d.reversed() ? new Color(0xB3, 0x26, 0x1E) : Color.BLACK));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(14);
            doc.add(title);
            Paragraph copy = new Paragraph("Student Copy", font(Font.BOLD, 10, Color.BLACK));
            copy.setAlignment(Element.ALIGN_CENTER);
            copy.setSpacingBefore(6);
            copy.setSpacingAfter(8);
            doc.add(copy);

            doc.add(infoBox(d));
            doc.add(particularsBox(d));

            Paragraph computer = new Paragraph("This is Computer Generated Receipt", font(Font.BOLD, 9, Color.BLACK));
            computer.setSpacingBefore(6);
            doc.add(computer);
            doc.add(feeSummary(d));
            doc.add(footer(d.institute(), brand));

            Paragraph note = new Paragraph("Note : Produce this receipt for future clarification in respect of fees paid.",
                    font(Font.BOLD, 9, Color.BLACK));
            note.setSpacingBefore(14);
            doc.add(note);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not draw the receipt", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------------------------------------

    /** The institute logo on the left, its name and address beside it. */
    private PdfPTable header(Data d, Color brand) throws DocumentException {
        PdfPTable band = new PdfPTable(new float[]{1.3f, 6f});
        band.setWidthPercentage(100);
        String name = d.institute() != null && d.institute().name() != null ? d.institute().name() : "Fee receipt";

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (d.logo() != null) {
            try {
                Image logo = Image.getInstance(d.logo());
                logo.scaleToFit(70, 70);
                logoCell.addElement(new Phrase(new Chunk(logo, 0, 0, true)));
            } catch (Exception ignored) {
                // a receipt without a logo rather than no receipt
            }
        }
        band.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        textCell.addElement(new Paragraph(name, font(Font.BOLD, 20, brand)));
        if (d.institute() != null && d.institute().address() != null && !d.institute().address().isBlank()) {
            textCell.addElement(new Paragraph(d.institute().address().trim(), font(Font.NORMAL, 9, MUTED)));
        }
        band.addCell(textCell);
        return band;
    }

    /** Receipt no, date, student, registration no, course and financial year in a bordered box. */
    private PdfPTable infoBox(Data d) {
        PdfPTable t = new PdfPTable(new float[]{1.3f, 3f, 1.5f, 2.4f});
        t.setWidthPercentage(100);
        info(t, "Receipt No :", d.receiptNo(), "Receipt Date :", d.date().format(DATE_SLASH));
        info(t, "Student :", d.studentName(), "Financial Yr :", financialYear(d.date()));
        info(t, "Reg. No :", d.studentCode(), "Course :", d.courseTitle());
        return boxed(t, 4);
    }

    private void info(PdfPTable t, String l1, String v1, String l2, String v2) {
        t.addCell(plain(l1, Font.BOLD, 10));
        t.addCell(plain(v1, Font.NORMAL, 10));
        t.addCell(plain(l2, Font.BOLD, 10));
        t.addCell(plain(v2, Font.NORMAL, 10));
    }

    private PdfPCell plain(String text, int style, float size) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font(style, size, Color.BLACK)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(4);
        c.setPaddingBottom(4);
        return c;
    }

    /** Wraps a table in a one-cell table with a thin outline. */
    private static PdfPTable boxed(PdfPTable inner, float padding) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(8);
        PdfPCell wrap = new PdfPCell(inner);
        wrap.setBorderColor(MUTED);
        wrap.setBorderWidth(0.8f);
        wrap.setPadding(padding);
        box.addCell(wrap);
        return box;
    }

    /** S.NO / PARTICULARS / AMOUNT, then total, amount in words, payment mode, reference and remarks. */
    private PdfPTable particularsBox(Data d) {
        PdfPTable t = new PdfPTable(new float[]{1f, 6f, 2f});
        t.setWidthPercentage(100);
        String[] heads = {"S.NO", "PARTICULARS", "AMOUNT"};
        for (int i = 0; i < 3; i++) {
            PdfPCell h = plain(heads[i], Font.BOLD, 10);
            h.setBorder(Rectangle.BOTTOM);
            h.setBorderWidth(0.8f);
            h.setHorizontalAlignment(i == 2 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            t.addCell(h);
        }
        java.util.List<Particular> lines = d.particulars() == null || d.particulars().isEmpty()
                ? java.util.List.of(new Particular(d.courseTitle() == null ? "Course fee" : d.courseTitle() + " - course fee",
                d.amount()))
                : d.particulars();
        int no = 1;
        for (Particular line : lines) {
            t.addCell(plain(String.valueOf(no++), Font.NORMAL, 10));
            t.addCell(plain(line.label(), Font.NORMAL, 10));
            PdfPCell amt = plain(money("", line.amount()).trim(), Font.NORMAL, 10);
            amt.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(amt);
        }
        PdfPCell blank = new PdfPCell(new Phrase(" "));
        blank.setBorder(Rectangle.NO_BORDER);
        blank.setColspan(3);
        blank.setFixedHeight(Math.max(30, 150 - 20 * lines.size()));
        t.addCell(blank);

        PdfPTable rows = new PdfPTable(new float[]{2f, 7f});
        rows.setWidthPercentage(100);
        PdfPCell tl = plain("Total", Font.BOLD, 10);
        tl.setHorizontalAlignment(Element.ALIGN_CENTER);
        rows.addCell(tl);
        PdfPCell tv = plain(money(d.currencySymbol(), d.amount()), Font.BOLD, 10);
        tv.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rows.addCell(tv);
        labelled(rows, "In words :", IndianWords.rupees(d.amount()));
        labelled(rows, "Pay't Mode :", pretty(d.method())
                + (d.referenceNo() == null || d.referenceNo().isBlank() ? "" : ", REF. NO : " + d.referenceNo()));
        labelled(rows, "Remarks :", d.reversed() ? "This payment was cancelled" : "Fees received");

        PdfPTable both = new PdfPTable(1);
        both.setWidthPercentage(100);
        PdfPCell a = new PdfPCell(t);
        a.setBorder(Rectangle.NO_BORDER);
        both.addCell(a);
        PdfPCell b = new PdfPCell(rows);
        b.setBorder(Rectangle.TOP);
        b.setBorderColor(MUTED);
        b.setBorderWidth(0.8f);
        both.addCell(b);
        return boxed(both, 0);
    }

    private void labelled(PdfPTable t, String label, String value) {
        PdfPCell l = plain(label, Font.BOLD, 10);
        l.setHorizontalAlignment(Element.ALIGN_CENTER);
        l.setBorder(Rectangle.TOP);
        l.setBorderColor(MUTED);
        PdfPCell v = plain(value, Font.BOLD, 10);
        v.setBorder(Rectangle.TOP);
        v.setBorderColor(MUTED);
        t.addCell(l);
        t.addCell(v);
    }

    /** Total fee, paid so far and balance, so the student sees where the fees stand. */
    private PdfPTable feeSummary(Data d) {
        PdfPTable t = new PdfPTable(new float[]{2f, 5f});
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingBefore(10);
        row(t, "Total fee", money(d.currencySymbol(), d.netFee()));
        row(t, "Paid so far", money(d.currencySymbol(), d.paidToDate()));
        row(t, "Balance", money(d.currencySymbol(), d.balance()));
        return t;
    }

    /** April to March, as fees are accounted in India: a date in Feb 2024 is 2023-2024. */
    static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return start + "-" + (start + 1);
    }

    /** The institute address, phone and email at the foot of the receipt. */
    private PdfPTable footer(BrandingClient.Branding b, Color brand) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(18);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(brand);
        c.setBorderWidth(1.2f);
        c.setPaddingTop(8);
        if (b != null) {
            if (b.name() != null && !b.name().isBlank()) {
                c.addElement(new Paragraph(b.name(), font(Font.BOLD, 10, INK)));
            }
            if (b.address() != null && !b.address().isBlank()) {
                c.addElement(new Paragraph(b.address().trim(), font(Font.NORMAL, 9, MUTED)));
            }
            Paragraph line = new Paragraph();
            line.setSpacingBefore(3);
            appendContact(line, "Phone: ", b.contactPhone());
            appendContact(line, "Email: ", b.contactEmail());
            appendContact(line, "Web: ", b.website());
            if (!line.isEmpty()) {
                c.addElement(line);
            }
        }
        t.addCell(c);
        return t;
    }

    private static void appendContact(Paragraph line, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!line.isEmpty()) {
            line.add(new Chunk("     ", font(Font.NORMAL, 9, MUTED)));
        }
        line.add(new Chunk(label, font(Font.NORMAL, 9, MUTED)));
        line.add(new Chunk(value.trim(), font(Font.BOLD, 9, INK)));
    }

    /** The logo, faint, behind the page; the institute name set diagonally when there is no logo. */
    private static final class Watermark extends com.lowagie.text.pdf.PdfPageEventHelper {
        private final Data data;

        Watermark(Data data) {
            this.data = data;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContentUnder();
            com.lowagie.text.pdf.PdfGState faint = new com.lowagie.text.pdf.PdfGState();
            faint.setFillOpacity(0.07f);
            cb.saveState();
            cb.setGState(faint);
            try {
                float width = document.getPageSize().getWidth();
                float height = document.getPageSize().getHeight();
                if (data.logo() != null) {
                    Image logo = Image.getInstance(data.logo());
                    logo.scaleToFit(330, 330);
                    logo.setAbsolutePosition((width - logo.getScaledWidth()) / 2, (height - logo.getScaledHeight()) / 2 - 30);
                    cb.addImage(logo);
                } else if (data.institute() != null && data.institute().name() != null) {
                    com.lowagie.text.pdf.BaseFont base = com.lowagie.text.pdf.BaseFont.createFont(
                            com.lowagie.text.pdf.BaseFont.HELVETICA_BOLD, com.lowagie.text.pdf.BaseFont.WINANSI, false);
                    cb.beginText();
                    cb.setFontAndSize(base, 56);
                    cb.setColorFill(brand(data.institute()));
                    cb.showTextAligned(Element.ALIGN_CENTER, data.institute().name(), width / 2, height / 2 - 30, 35);
                    cb.endText();
                }
            } catch (Exception ignored) {
                // no watermark rather than no receipt
            } finally {
                cb.restoreState();
            }
        }
    }

    private void row(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, font(Font.NORMAL, 10, MUTED)));
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "" : value, font(Font.BOLD, 10, INK)));
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(LINE);
            c.setBorderWidth(0.6f);
            c.setPaddingTop(6);
            c.setPaddingBottom(6);
            t.addCell(c);
        }
    }

    // ------------------------------------------------------------------------------------------------

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

    private static String pretty(String method) {
        if (method == null || method.isEmpty()) {
            return "";
        }
        return switch (method) {
            case "BANK_TRANSFER" -> "Bank transfer";
            case "ONLINE" -> "Online (Razorpay)";
            default -> method.charAt(0) + method.substring(1).toLowerCase(Locale.ENGLISH);
        };
    }

    /** "Rs. 15,00,000.00": the last three digits, then pairs, as amounts are written in India. */
    static String money(String symbol, BigDecimal amount) {
        BigDecimal v = (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP);
        String[] parts = v.abs().toPlainString().split("[.]");
        String digits = parts[0];
        StringBuilder grouped = new StringBuilder();
        int head = digits.length() - 3;
        if (head > 0) {
            for (int i = 0; i < head; i++) {
                if (i > 0 && (head - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(digits.charAt(i));
            }
            grouped.append(',').append(digits, head, digits.length());
        } else {
            grouped.append(digits);
        }
        return (symbol == null ? "Rs." : symbol) + " " + (v.signum() < 0 ? "-" : "") + grouped + "." + parts[1];
    }
}
