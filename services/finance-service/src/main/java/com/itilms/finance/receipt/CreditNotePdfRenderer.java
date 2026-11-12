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
 * A credit note for a refund: it says which receipt it cancels in part or in whole, how much is being returned, and
 * the tax that goes back with it. Same plain boxed layout as the receipt.
 */
@Component
public class CreditNotePdfRenderer {

    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color DEFAULT_BRAND = new Color(0xE0, 0x66, 0x1A);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    public record Data(String noteNo, LocalDate date, String receiptNo, LocalDate receiptDate, String studentName,
                       String studentCode, String courseTitle, String currencySymbol, BigDecimal amount, String reason,
                       String status, ReceiptPdfRenderer.TaxInfo tax, String gstin, String sacCode,
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

            Paragraph title = new Paragraph("CREDIT NOTE", font(Font.BOLD, 12, Color.BLACK));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(14);
            title.setSpacingAfter(8);
            doc.add(title);

            PdfPTable info = new PdfPTable(new float[]{1.5f, 3f, 1.6f, 2.4f});
            info.setWidthPercentage(100);
            info(info, "Credit Note No :", d.noteNo(), "Date :", d.date().format(DATE));
            info(info, "Against Receipt :", d.receiptNo(), "Receipt Date :", d.receiptDate().format(DATE));
            info(info, "Student :", d.studentName(), "Reg. No :", d.studentCode());
            info(info, "Course :", d.courseTitle(), "Status :", d.status());
            doc.add(boxed(info));

            PdfPTable rows = new PdfPTable(new float[]{2f, 7f});
            rows.setWidthPercentage(100);
            String sym = d.currencySymbol();
            line(rows, "Refunded :", ReceiptPdfRenderer.money(sym, d.amount()), true);
            line(rows, "In words :", IndianWords.rupees(d.amount()), false);
            if (d.tax() != null && d.tax().charged()) {
                var t = d.tax();
                line(rows, "Taxable value :", ReceiptPdfRenderer.money(sym, t.taxable()), false);
                String half = t.rate().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                if (t.igst().signum() > 0) {
                    line(rows, "IGST @ " + t.rate().stripTrailingZeros().toPlainString() + "% :", ReceiptPdfRenderer.money(sym, t.igst()), false);
                } else {
                    line(rows, "CGST @ " + half + "% :", ReceiptPdfRenderer.money(sym, t.cgst()), false);
                    line(rows, "SGST @ " + half + "% :", ReceiptPdfRenderer.money(sym, t.sgst()), false);
                }
                if (t.placeOfSupply() != null && !t.placeOfSupply().isBlank()) {
                    line(rows, "Place of Supply :", t.placeOfSupply(), false);
                }
                if (d.gstin() != null && !d.gstin().isBlank()) {
                    line(rows, "GSTIN :", d.gstin() + (d.sacCode() == null || d.sacCode().isBlank() ? "" : "   SAC : " + d.sacCode()), false);
                }
            } else {
                line(rows, "Tax :", "No GST was charged on the receipt", false);
            }
            line(rows, "Reason :", d.reason(), false);
            doc.add(boxed(rows));

            Paragraph note = new Paragraph("This credit note reduces the tax charged on the receipt above by the tax shown. "
                    + "It is computer generated and needs no signature.", font(Font.BOLD, 9, Color.BLACK));
            note.setSpacingBefore(10);
            doc.add(note);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not draw the credit note", e);
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
                // a note without a logo rather than no note
            }
        }
        band.addCell(logoCell);
        PdfPCell text = new PdfPCell();
        text.setBorder(Rectangle.NO_BORDER);
        text.setVerticalAlignment(Element.ALIGN_MIDDLE);
        String name = d.institute() != null && d.institute().name() != null ? d.institute().name() : "Credit note";
        text.addElement(new Paragraph(name, font(Font.BOLD, 20, brand)));
        if (d.institute() != null && d.institute().address() != null && !d.institute().address().isBlank()) {
            text.addElement(new Paragraph(d.institute().address().trim(), font(Font.NORMAL, 9, MUTED)));
        }
        if (d.gstin() != null && !d.gstin().isBlank() && d.tax() != null && d.tax().charged()) {
            text.addElement(new Paragraph("GST No: " + d.gstin().trim(), font(Font.BOLD, 9, Color.BLACK)));
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
        t.addCell(cell(l1, Font.BOLD));
        t.addCell(cell(v1, Font.NORMAL));
        t.addCell(cell(l2, Font.BOLD));
        t.addCell(cell(v2, Font.NORMAL));
    }

    private void line(PdfPTable t, String label, String value, boolean first) {
        PdfPCell l = cell(label, Font.BOLD);
        PdfPCell v = cell(value, Font.BOLD);
        if (!first) {
            l.setBorder(Rectangle.TOP);
            v.setBorder(Rectangle.TOP);
            l.setBorderColor(MUTED);
            v.setBorderColor(MUTED);
        }
        t.addCell(l);
        t.addCell(v);
    }

    private static PdfPCell cell(String text, int style) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font(style, 10, Color.BLACK)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(4);
        c.setPaddingBottom(4);
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
