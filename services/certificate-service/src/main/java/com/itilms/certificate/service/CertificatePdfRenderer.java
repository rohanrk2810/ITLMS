package com.itilms.certificate.service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.entity.Certificate;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;

/**
 * Draws the printable certificate (Doc S15.1: "PDF for ... certificates").
 *
 * <p>Rendered on request rather than stored: the certificate row is the record,
 * and the PDF is a view of it. A revoked certificate therefore cannot be
 * downloaded again from a copy that was saved before it was revoked.
 *
 * <p>Uses the PDF standard fonts, which cover Latin script. A name recorded in
 * another script needs an embedded font added here.
 */
@Component
@RequiredArgsConstructor
public class CertificatePdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final CertificateProperties props;

    public byte[] render(Certificate certificate, String verificationUrl) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 72, 72, 60, 60);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle("Certificate " + certificate.getCertificateNo());
        document.addAuthor(props.getInstituteName());
        document.open();

        drawBorder(writer.getDirectContent(), document.getPageSize());

        document.add(centred(props.getInstituteName(), font(FontFactory.HELVETICA_BOLD, 24), 0));
        document.add(centred("CERTIFICATE OF COMPLETION", font(FontFactory.HELVETICA, 16), 18));
        document.add(centred("This is to certify that", font(FontFactory.HELVETICA_OBLIQUE, 14), 40));
        document.add(centred(certificate.getStudentName(), font(FontFactory.HELVETICA_BOLD, 30), 14));
        document.add(centred("has successfully completed the course", font(FontFactory.HELVETICA_OBLIQUE, 14), 14));
        document.add(centred(certificate.getCourseTitle(), font(FontFactory.HELVETICA_BOLD, 22), 14));
        document.add(centred("Issued on " + DATE.format(certificate.getIssueDate()),
                font(FontFactory.HELVETICA, 12), 30));

        document.add(centred(props.getSignatoryName(), font(FontFactory.HELVETICA_BOLD, 12), 50));
        document.add(centred(props.getInstituteName(), font(FontFactory.HELVETICA, 10), 2));

        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(28);
        Font small = font(FontFactory.HELVETICA, 9);
        footer.add(new Chunk("Certificate no. " + certificate.getCertificateNo()
                + "   |   Verification code " + certificate.getVerificationCode(), small));
        footer.add(Chunk.NEWLINE);
        footer.add(new Chunk("Verify at " + verificationUrl, small));
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private static void drawBorder(PdfContentByte canvas, Rectangle page) {
        canvas.setLineWidth(3f);
        canvas.rectangle(24, 24, page.getWidth() - 48, page.getHeight() - 48);
        canvas.stroke();
        canvas.setLineWidth(0.75f);
        canvas.rectangle(32, 32, page.getWidth() - 64, page.getHeight() - 64);
        canvas.stroke();
    }

    private static Paragraph centred(String text, Font font, float spacingBefore) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingBefore(spacingBefore);
        return paragraph;
    }

    private static Font font(String name, float size) {
        return FontFactory.getFont(name, size);
    }
}
