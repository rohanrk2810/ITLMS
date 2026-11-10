package com.itilms.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.entity.IdCard;
import com.itilms.certificate.entity.IdCardPersonType;

class IdCardPdfRendererTest {

    private final IdCardPdfRenderer renderer =
            new IdCardPdfRenderer(new InstituteBranding(null, new CertificateProperties()));

    private static IdCard card(String name, String code) {
        return IdCard.builder().id(1L).cardNo("STU-2026-0042").verificationCode("K7QMX-2HWRP")
                .personType(IdCardPersonType.STUDENT).personCode(code).fullName(name).designation("Student")
                .batchName("Full Stack Java - Morning").issuedOn(LocalDate.of(2026, 11, 1))
                .validUntil(LocalDate.of(2027, 4, 30)).build();
    }

    private static byte[] samplePhoto() throws Exception {
        BufferedImage image = new BufferedImage(300, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(180, 200, 220));
        g.fillRect(0, 0, 300, 360);
        g.setColor(new Color(90, 110, 140));
        g.fillOval(100, 60, 100, 120);
        g.fillOval(50, 190, 200, 200);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String verifyUrl(IdCard c) {
        return "https://institute.example/verify-id/" + c.getCardNo() + "?code=" + c.getVerificationCode();
    }

    @Test
    void oneCardIsACardSizedPdf() throws Exception {
        IdCard card = card("Asha Patil", "STU-30");

        byte[] pdf = renderer.renderOne(card, samplePhoto(), verifyUrl(card));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(1500);
        Path out = Path.of("target", "id-card-sample.pdf");
        Files.write(out, pdf);
    }

    @Test
    void theInstituteAddressIsPrintedUnderTheNameAndTheCardNumberIsABarcode() throws Exception {
        var client = org.mockito.Mockito.mock(com.itilms.certificate.client.BrandingClient.class);
        org.mockito.Mockito.when(client.branding()).thenReturn(new com.itilms.certificate.client.BrandingClient.Branding(
                "Sprint IT Academy", null, null, null, 1L, "#2b0255", "+91 98000 00000",
                "Office 4, Sutra Plaza, FC Road, Pune 411004"));
        var withAddress = new IdCardPdfRenderer(new InstituteBranding(client, new CertificateProperties()));
        IdCard card = card("Asha Patil", "STU-30");

        byte[] pdf = withAddress.renderOne(card, samplePhoto(), verifyUrl(card));

        var reader = new com.lowagie.text.pdf.PdfReader(pdf);
        String text = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("Sprint IT Academy").contains("Sutra Plaza").contains("FC Road").contains("STU-2026-0042");
        // the QR code is drawn as squares; the barcode and the photo are pictures
        var pictures = reader.getPageN(1).getAsDict(com.lowagie.text.pdf.PdfName.RESOURCES)
                .getAsDict(com.lowagie.text.pdf.PdfName.XOBJECT);
        assertThat(pictures.getKeys()).hasSizeGreaterThanOrEqualTo(2); // the photo and the barcode
        Files.write(Path.of("target", "id-card-with-address.pdf"), pdf);
    }

    @Test
    void withoutAnAddressTheHeaderIsAsBefore_andALongAddressIsShortened() throws Exception {
        var client = org.mockito.Mockito.mock(com.itilms.certificate.client.BrandingClient.class);
        org.mockito.Mockito.when(client.branding()).thenReturn(new com.itilms.certificate.client.BrandingClient.Branding(
                "Sprint IT Academy", null, null, null, 1L, null, null, "x ".repeat(300)));
        var withLong = new IdCardPdfRenderer(new InstituteBranding(client, new CertificateProperties()));
        IdCard card = card("Asha Patil", "STU-30");

        byte[] pdf = withLong.renderOne(card, null, verifyUrl(card));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void aLongNameAndNoPhotoStillDrawAndNeverFail() {
        IdCard card = card("Venkata Subramanian Ramakrishnan Iyer-Chandrasekharan", null);
        card.setBatchName(null);

        byte[] pdf = renderer.renderOne(card, null, verifyUrl(card));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void aGarbagePhotoFallsBackToTheInitial() {
        IdCard card = card("Asha Patil", "STU-30");

        byte[] pdf = renderer.renderOne(card, new byte[]{1, 2, 3, 4}, verifyUrl(card));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void tenCardsNeedASecondSheet() throws Exception {
        List<IdCardPdfRenderer.Item> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            IdCard c = card("Student Number " + i, "STU-" + i);
            items.add(new IdCardPdfRenderer.Item(c, i % 2 == 0 ? samplePhoto() : null, verifyUrl(c)));
        }

        byte[] pdf = renderer.renderSheet(items);

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        String text = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(text.split("/Type\\s*/Page[^s]", -1).length - 1).isEqualTo(2);
        Files.write(Path.of("target", "id-card-sheet-sample.pdf"), pdf);
    }
}
