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
 * Draws a fee receipt as a one-page A4 PDF: a header in the institute colour with its logo, who paid and for what, the
 * amount large with its words, how it was paid, and where the fees stand. Text uses the standard PDF fonts, which
 * cover Latin script only, so money is shown as "Rs." and not with the rupee sign.
 */
@Component
public class ReceiptPdfRenderer {

    private static final Color DEFAULT_BRAND = new Color(0xE0, 0x66, 0x1A);
    private static final Color INK = new Color(0x22, 0x22, 0x22);
    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color LINE = new Color(0xDD, 0xDD, 0xDD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /** Everything on the receipt. The institute and the logo may be null. */
    public record Data(String receiptNo, LocalDate date, String studentName, String studentCode,
                       String courseTitle, String currencySymbol, BigDecimal amount, String method, String referenceNo,
                       BigDecimal netFee, BigDecimal paidToDate, BigDecimal balance, boolean reversed,
                       BrandingClient.Branding institute, byte[] logo) {
    }

    public byte[] render(Data d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 0, 0, 0, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Watermark(d));
            doc.open();
            Color brand = brand(d.institute());
            doc.add(header(d, brand));

            PdfPTable body = new PdfPTable(1);
            body.setWidthPercentage(100);
            PdfPCell wrap = new PdfPCell();
            wrap.setBorder(Rectangle.NO_BORDER);
            wrap.setPaddingLeft(40);
            wrap.setPaddingRight(40);
            wrap.setPaddingTop(24);

            wrap.addElement(new Paragraph(d.reversed() ? "FEE RECEIPT - CANCELLED" : "FEE RECEIPT",
                    font(Font.BOLD, 20, d.reversed() ? new Color(0xB3, 0x26, 0x1E) : INK)));
            Paragraph sub = new Paragraph("Receipt no. " + d.receiptNo() + "    Date " + d.date().format(DATE),
                    font(Font.NORMAL, 10, MUTED));
            sub.setSpacingAfter(16);
            wrap.addElement(sub);

            PdfPTable who = new PdfPTable(2);
            who.setWidthPercentage(100);
            who.setSpacingAfter(14);
            who.addCell(block("RECEIVED FROM", d.studentName(), d.studentCode() == null ? "" : "Student ID " + d.studentCode()));
            who.addCell(block("TOWARDS", d.courseTitle() == null ? "Course fee" : d.courseTitle(), "Course fee"));
            wrap.addElement(who);

            wrap.addElement(amountBox(d, brand));
            wrap.addElement(detailTable(d));
            wrap.addElement(summaryTable(d));

            Paragraph thanks = new Paragraph("Thank you for your payment.", font(Font.BOLD, 11, INK));
            thanks.setSpacingBefore(22);
            wrap.addElement(thanks);
            Paragraph note = new Paragraph("This is a computer-generated receipt and needs no signature. "
                    + "Please keep it for your records.", font(Font.NORMAL, 8.5f, MUTED));
            note.setSpacingBefore(4);
            wrap.addElement(note);
            wrap.addElement(footer(d.institute(), brand));
            body.addCell(wrap);
            doc.add(body);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not draw the receipt", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------------------------------------

    private PdfPTable header(Data d, Color brand) throws DocumentException {
        PdfPTable band = new PdfPTable(new float[]{1.2f, 6f});
        band.setWidthPercentage(100);
        String name = d.institute() != null && d.institute().name() != null ? d.institute().name() : "Fee receipt";

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBackgroundColor(brand);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPaddingLeft(40);
        logoCell.setPaddingTop(22);
        logoCell.setPaddingBottom(22);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (d.logo() != null) {
            try {
                Image logo = Image.getInstance(d.logo());
                logo.scaleToFit(52, 52);
                logoCell.addElement(new Phrase(new Chunk(logo, 0, 0, true)));
            } catch (Exception ignored) {
                // a receipt without a logo rather than no receipt
            }
        }
        band.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBackgroundColor(brand);
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setPaddingTop(22);
        textCell.setPaddingBottom(22);
        textCell.setPaddingRight(40);
        textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        textCell.addElement(new Paragraph(name, font(Font.BOLD, 18, Color.WHITE)));
        if (d.institute() != null && d.institute().address() != null && !d.institute().address().isBlank()) {
            textCell.addElement(new Paragraph(d.institute().address().trim(), font(Font.NORMAL, 9, Color.WHITE)));
        }
        band.addCell(textCell);
        return band;
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

    private PdfPCell block(String label, String main, String sub) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(6);
        c.addElement(new Paragraph(label, font(Font.BOLD, 8, MUTED)));
        c.addElement(new Paragraph(main == null ? "" : main, font(Font.BOLD, 12, INK)));
        if (sub != null && !sub.isBlank()) {
            c.addElement(new Paragraph(sub, font(Font.NORMAL, 9, MUTED)));
        }
        return c;
    }

    private PdfPTable amountBox(Data d, Color brand) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(16);
        PdfPCell c = new PdfPCell();
        c.setBorderColor(brand);
        c.setBorderWidth(1.2f);
        c.setPadding(14);
        c.addElement(new Paragraph("AMOUNT RECEIVED", font(Font.BOLD, 8, MUTED)));
        Paragraph amount = new Paragraph(money(d.currencySymbol(), d.amount()), font(Font.BOLD, 26, brand));
        amount.setSpacingBefore(2);
        c.addElement(amount);
        c.addElement(new Paragraph(IndianWords.rupees(d.amount()), font(Font.ITALIC, 9.5f, MUTED)));
        box.addCell(c);
        return box;
    }

    private PdfPTable detailTable(Data d) {
        PdfPTable t = new PdfPTable(new float[]{2f, 5f});
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);
        row(t, "Payment method", pretty(d.method()));
        if (d.referenceNo() != null && !d.referenceNo().isBlank()) {
            row(t, "Reference", d.referenceNo());
        }
        row(t, "Payment date", d.date().format(DATE));
        return t;
    }

    private PdfPTable summaryTable(Data d) {
        PdfPTable t = new PdfPTable(new float[]{2f, 5f});
        t.setWidthPercentage(100);
        PdfPCell title = new PdfPCell(new Phrase("FEE SUMMARY", font(Font.BOLD, 8, MUTED)));
        title.setColspan(2);
        title.setBorder(Rectangle.NO_BORDER);
        title.setPaddingBottom(4);
        t.addCell(title);
        row(t, "Total fee", money(d.currencySymbol(), d.netFee()));
        row(t, "Paid so far", money(d.currencySymbol(), d.paidToDate()));
        row(t, "Balance", money(d.currencySymbol(), d.balance()));
        return t;
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
