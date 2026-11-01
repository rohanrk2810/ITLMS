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
