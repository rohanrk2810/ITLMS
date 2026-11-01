package com.itilms.certificate.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.itilms.certificate.entity.IdCard;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;

/**
 * Draws identity cards: one card-sized PDF, or a sheet of cards on A4 to print and cut.
 *
 * <p>The card is CR80 (the credit-card size, 54 x 85.6 mm), upright: institute band, photo, name, role, code,
 * batch, valid-until date, and a QR code that opens the public check page. Drawn on request from the stored row,
 * so a revoked card cannot be printed again from here.
 *
 * <p>Uses the PDF standard fonts (Latin script). A name in another script needs an embedded font added here.
 */
@Component
@RequiredArgsConstructor
public class IdCardPdfRenderer {

    /** One card to draw. {@code photo} may be null. */
    public record Item(IdCard card, byte[] photo, String verificationUrl) {
    }

    static final float W = 153f;
    static final float H = 243f;
    private static final float SHEET_GAP = 12f;
    private static final Color BRAND = new Color(0xE0, 0x66, 0x1A);
    private static final Color INK = new Color(0x22, 0x22, 0x22);
    private static final Color MUTED = new Color(0x66, 0x66, 0x66);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final InstituteBranding branding;

    /** The card on its own page, the size of the card. */
    public byte[] renderOne(IdCard card, byte[] photo, String verificationUrl) {
        InstituteBranding.Info institute = branding.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(new Rectangle(W, H), 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle("ID card " + card.getCardNo());
        document.addAuthor(institute.name());
        document.open();
        writer.setPageEmpty(false);
        drawCard(writer.getDirectContent(), 0, 0, new Item(card, photo, verificationUrl), institute);
        document.close();
        return out.toByteArray();
    }

    /** Cards laid out three across and three down on each A4 page. */
    public byte[] renderSheet(List<Item> items) {
        InstituteBranding.Info institute = branding.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle("ID cards");
        document.addAuthor(institute.name());
        document.open();
        writer.setPageEmpty(false);

        int cols = 3;
        int rows = 3;
        float left = (PageSize.A4.getWidth() - (cols * W + (cols - 1) * SHEET_GAP)) / 2;
        float top = PageSize.A4.getHeight() - 36f;
        int perPage = cols * rows;
        PdfContentByte canvas = writer.getDirectContent();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0 && i % perPage == 0) {
                document.newPage();
                writer.setPageEmpty(false);
                canvas = writer.getDirectContent();
            }
            int slot = i % perPage;
            float x = left + (slot % cols) * (W + SHEET_GAP);
            float y = top - ((slot / cols) + 1) * H - (slot / cols) * SHEET_GAP;
            drawCard(canvas, x, y, items.get(i), institute);
        }
        document.close();
        return out.toByteArray();
    }

    // ------------------------------------------------------------------------------------------------

    private void drawCard(PdfContentByte cb, float x, float y, Item item, InstituteBranding.Info institute) {
        IdCard card = item.card();

        // outline and header band
        cb.saveState();
        cb.setColorStroke(new Color(0xBB, 0xBB, 0xBB));
        cb.setLineWidth(0.6f);
        cb.roundRectangle(x, y, W, H, 8);
        cb.stroke();
        cb.setColorFill(BRAND);
        cb.rectangle(x + 0.3f, y + H - 44f, W - 0.6f, 43.7f);
        cb.fill();
        cb.restoreState();

        // logo and institute name in the band
        float textLeft = x + 8;
        float textWidth = W - 16;
        if (institute.logo() != null) {
            try {
                Image logo = Image.getInstance(institute.logo());
                logo.scaleToFit(28, 28);
                logo.setAbsolutePosition(x + 8, y + H - 36f);
                cb.addImage(logo);
                textLeft = x + 44;
                textWidth = W - 52;
            } catch (Exception ignored) {
                // no logo rather than no card
            }
        }
        text(cb, institute.name(), BaseFont.HELVETICA_BOLD, 9f, Color.WHITE, textLeft, y + H - 46f, textWidth, 32f,
                textLeft == x + 8 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);

        // photo
        float photoW = 58f;
        float photoH = 68f;
        float photoX = x + (W - photoW) / 2;
        float photoY = y + H - 52f - photoH;
        cb.saveState();
        cb.setColorStroke(BRAND);
        cb.setLineWidth(1.2f);
        cb.rectangle(photoX, photoY, photoW, photoH);
        cb.stroke();
        cb.restoreState();
        boolean drawn = false;
        if (item.photo() != null) {
            try {
                Image picture = Image.getInstance(item.photo());
                picture.scaleToFit(photoW - 2, photoH - 2);
                picture.setAbsolutePosition(photoX + (photoW - picture.getScaledWidth()) / 2,
                        photoY + (photoH - picture.getScaledHeight()) / 2);
                cb.addImage(picture);
                drawn = true;
            } catch (Exception ignored) {
                // an unreadable photo falls back to the initial below
            }
        }
        if (!drawn) {
            String initial = card.getFullName().isBlank() ? "?" : card.getFullName().substring(0, 1).toUpperCase();
            text(cb, initial, BaseFont.HELVETICA_BOLD, 30f, new Color(0xBB, 0xBB, 0xBB), photoX, photoY + 22f,
                    photoW, 36f, Element.ALIGN_CENTER);
        }

        // name, role, code, batch, validity
        float cy = photoY - 8f;
        cy = line(cb, card.getFullName(), BaseFont.HELVETICA_BOLD, 10.5f, INK, x, cy, true);
        cy = line(cb, card.getDesignation() == null ? "" : card.getDesignation().toUpperCase(Locale.ENGLISH),
                BaseFont.HELVETICA_BOLD, 7.5f, BRAND, x, cy, false);
        if (card.getPersonCode() != null && !card.getPersonCode().isBlank()) {
            cy = line(cb, "ID " + card.getPersonCode(), BaseFont.HELVETICA, 7.5f, INK, x, cy, false);
        }
        if (card.getBatchName() != null && !card.getBatchName().isBlank()) {
            cy = line(cb, card.getBatchName(), BaseFont.HELVETICA, 7f, MUTED, x, cy, false);
        }
        line(cb, "Valid until " + DATE.format(card.getValidUntil()), BaseFont.HELVETICA, 7f, MUTED, x, cy, false);

        // QR and card number
        drawQr(cb, item.verificationUrl(), x + (W - 38f) / 2, y + 15f, 38f);
        text(cb, card.getCardNo(), BaseFont.HELVETICA, 6.5f, MUTED, x, y + 6f, W, 9f, Element.ALIGN_CENTER);
    }

    /** The QR code as filled squares, so it stays sharp at any print size. A failure leaves it blank. */
    private static void drawQr(PdfContentByte cb, String content, float x, float y, float size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
            float cell = size / matrix.getWidth();
            cb.saveState();
            cb.setColorFill(Color.BLACK);
            for (int row = 0; row < matrix.getHeight(); row++) {
                for (int col = 0; col < matrix.getWidth(); col++) {
                    if (matrix.get(col, row)) {
                        cb.rectangle(x + col * cell, y + size - (row + 1) * cell, cell + 0.15f, cell + 0.15f);
                    }
                }
            }
            cb.fill();
            cb.restoreState();
        } catch (Exception ignored) {
            // the number below still lets someone verify by hand
        }
    }

    /** One centred line that shrinks to fit; returns the next line's baseline. */
    private float line(PdfContentByte cb, String value, String font, float size, Color color, float x, float y,
                       boolean tall) {
        if (value == null || value.isBlank()) {
            return y;
        }
        float fitted = fit(value, font, size, W - 14f);
        text(cb, value, font, fitted, color, x, y - fitted, W, fitted + 2f, Element.ALIGN_CENTER);
        return y - fitted - (tall ? 5f : 3f);
    }

    private static float fit(String value, String font, float size, float maxWidth) {
        try {
            BaseFont base = BaseFont.createFont(font, BaseFont.WINANSI, false);
            float s = size;
            while (s > 5.5f && base.getWidthPoint(value, s) > maxWidth) {
                s -= 0.5f;
            }
            return s;
        } catch (Exception e) {
            return size;
        }
    }

    private static void text(PdfContentByte cb, String value, String font, float size, Color color, float x, float y,
                             float width, float height, int align) {
        try {
            BaseFont base = BaseFont.createFont(font, BaseFont.WINANSI, false);
            com.lowagie.text.Font f = new com.lowagie.text.Font(base, size, com.lowagie.text.Font.NORMAL, color);
            ColumnText ct = new ColumnText(cb);
            ct.setSimpleColumn(x, y - 2f, x + width, y + height);
            ct.setLeading(size + 1.5f);
            ct.setAlignment(align);
            ct.addText(new Phrase(value, f));
            ct.go();
        } catch (Exception e) {
            // text that cannot be drawn is skipped; the rest of the card still prints
        }
    }
}
