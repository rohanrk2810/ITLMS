package com.itilms.finance.receipt;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * A student's statement of fees: for each course what was charged, every payment received against it and what is still
 * to pay. Drawn like the receipt (logo and name on top, plain boxed tables) on A4; it runs onto more pages when a
 * student has many payments.
 */
@Component
public class StatementPdfRenderer {

    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color DEFAULT_BRAND = new Color(0xE0, 0x66, 0x1A);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    public record PaymentRow(LocalDate date, String receiptNo, String method, String reference, BigDecimal amount,
                             boolean reversed) {
    }

    public record CourseBlock(String courseTitle, BigDecimal netFee, BigDecimal paid, BigDecimal outstanding,
                              List<PaymentRow> payments) {
    }

    public record Data(String studentName, String studentCode, String currencySymbol, LocalDate asOf,
                       List<CourseBlock> courses, BrandingClient.Branding institute, byte[] logo) {
    }

    public byte[] render(Data d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 30, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Color brand = brand(d.institute());
            doc.add(header(d, brand));

            Paragraph title = new Paragraph("STATEMENT OF FEES", font(Font.BOLD, 12, Color.BLACK));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(14);
            title.setSpacingAfter(8);
            doc.add(title);

            PdfPTable who = new PdfPTable(new float[]{1.3f, 3f, 1.5f, 2.4f});
            who.setWidthPercentage(100);
            who.addCell(cell("Student :", Font.BOLD));
            who.addCell(cell(d.studentName(), Font.NORMAL));
            who.addCell(cell("As on :", Font.BOLD));
            who.addCell(cell(d.asOf().format(DATE), Font.NORMAL));
            who.addCell(cell("Reg. No :", Font.BOLD));
            who.addCell(cell(d.studentCode(), Font.NORMAL));
            who.addCell(cell("", Font.NORMAL));
            who.addCell(cell("", Font.NORMAL));
            doc.add(who);

            BigDecimal totalPaid = BigDecimal.ZERO;
            BigDecimal totalDue = BigDecimal.ZERO;
            for (CourseBlock c : d.courses()) {
                doc.add(block(c, d.currencySymbol()));
                totalPaid = totalPaid.add(c.paid());
                totalDue = totalDue.add(c.outstanding());
            }
            if (d.courses().isEmpty()) {
                Paragraph none = new Paragraph("No fees have been raised for this student.", font(Font.NORMAL, 10, MUTED));
                none.setSpacingBefore(12);
                doc.add(none);
            } else {
                PdfPTable sum = new PdfPTable(new float[]{5f, 2f});
                sum.setWidthPercentage(60);
                sum.setHorizontalAlignment(Element.ALIGN_LEFT);
                sum.setSpacingBefore(12);
                sum.addCell(cell("Total paid", Font.BOLD));
                sum.addCell(right(ReceiptPdfRenderer.money(d.currencySymbol(), totalPaid), Font.BOLD));
                sum.addCell(cell("Total still to pay", Font.BOLD));
                sum.addCell(right(ReceiptPdfRenderer.money(d.currencySymbol(), totalDue), Font.BOLD));
                doc.add(sum);
            }

            Paragraph note = new Paragraph("This is a computer generated statement and needs no signature.",
                    font(Font.BOLD, 9, Color.BLACK));
            note.setSpacingBefore(18);
            doc.add(note);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not draw the statement", e);
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
                // a statement without a logo rather than no statement
            }
        }
        band.addCell(logoCell);
        PdfPCell text = new PdfPCell();
        text.setBorder(Rectangle.NO_BORDER);
        text.setVerticalAlignment(Element.ALIGN_MIDDLE);
        String name = d.institute() != null && d.institute().name() != null ? d.institute().name() : "Statement of fees";
        text.addElement(new Paragraph(name, font(Font.BOLD, 20, brand)));
        if (d.institute() != null && d.institute().address() != null && !d.institute().address().isBlank()) {
            text.addElement(new Paragraph(d.institute().address().trim(), font(Font.NORMAL, 9, MUTED)));
        }
        band.addCell(text);
        return band;
    }

    private PdfPTable block(CourseBlock c, String symbol) {
        PdfPTable t = new PdfPTable(new float[]{2f, 2f, 2f, 3f, 2f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(14);
        PdfPCell head = cell(c.courseTitle() == null ? "Course fee" : c.courseTitle(), Font.BOLD);
        head.setColspan(5);
        head.setBorder(Rectangle.BOTTOM);
        head.setBorderWidth(0.8f);
        t.addCell(head);
        PdfPCell fee = cell("Net fee " + ReceiptPdfRenderer.money(symbol, c.netFee()) + "   Paid "
                + ReceiptPdfRenderer.money(symbol, c.paid()) + "   Balance " + ReceiptPdfRenderer.money(symbol, c.outstanding()),
                Font.NORMAL);
        fee.setColspan(5);
        t.addCell(fee);
        for (String h : new String[]{"Date", "Receipt No", "Mode", "Reference", "Amount"}) {
            PdfPCell hc = cell(h, Font.BOLD);
            hc.setBorder(Rectangle.BOTTOM);
            hc.setBorderColor(MUTED);
            if (h.equals("Amount")) {
                hc.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(hc);
        }
        if (c.payments().isEmpty()) {
            PdfPCell none = cell("No payment received yet.", Font.NORMAL);
            none.setColspan(5);
            t.addCell(none);
        }
        for (PaymentRow p : c.payments()) {
            t.addCell(cell(p.date().format(DATE), Font.NORMAL));
            t.addCell(cell(p.receiptNo() + (p.reversed() ? " (cancelled)" : ""), Font.NORMAL));
            t.addCell(cell(p.method(), Font.NORMAL));
            t.addCell(cell(p.reference() == null ? "" : p.reference(), Font.NORMAL));
            t.addCell(right((p.reversed() ? "-" : "") + ReceiptPdfRenderer.money("", p.amount()).trim(), Font.NORMAL));
        }
        return t;
    }

    private static PdfPCell cell(String text, int style) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font(style, 9.5f, Color.BLACK)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(3);
        c.setPaddingBottom(3);
        return c;
    }

    private static PdfPCell right(String text, int style) {
        PdfPCell c = cell(text, style);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
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
