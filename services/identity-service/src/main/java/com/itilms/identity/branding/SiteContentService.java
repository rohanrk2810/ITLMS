package com.itilms.identity.branding;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/** What the public website says and the pictures it shows. Reading is anonymous; every write is the administrator's. */
@Service
@RequiredArgsConstructor
public class SiteContentService {

    private static final String SERVICE_NAME = "identity-service";
    static final int MAX_CONFIG_CHARS = 60_000;
    /** The browser shrinks pictures before sending; this is the ceiling for anything that arrives. */
    static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    /** Bounds how much the database can grow if an administrator keeps uploading and never removes. */
    static final int MAX_IMAGES = 120;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final InstituteSettingsRepository settings;
    private final SiteImageRepository images;
    private final EventPublisher events;

    @Transactional(readOnly = true)
    public SiteConfigResponse get() {
        return new SiteConfigResponse(load().getSiteConfig());
    }

    @Transactional
    public SiteConfigResponse set(String json, Long adminId) {
        try {
            if (!JSON.readTree(json).isObject()) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new BusinessRuleException("INVALID_SITE_CONFIG", "The website content is not valid JSON.");
        }
        InstituteSettings s = load();
        s.setSiteConfig(json);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(adminId);
        settings.save(s);
        events.audit(SERVICE_NAME, "WEBSITE_CONTENT_UPDATED", "InstituteSettings", (long) InstituteSettings.ROW_ID,
                adminId, Map.of("name", s.getName()));
        return new SiteConfigResponse(json);
    }

    @Transactional
    public SiteImageResponse upload(byte[] bytes, Long adminId) {
        String type = ImageSniffer.detect(bytes, MAX_IMAGE_BYTES);
        if (images.count() >= MAX_IMAGES) {
            throw new BusinessRuleException("TOO_MANY_SITE_IMAGES",
                    "The website already holds " + MAX_IMAGES + " pictures. Remove some you no longer use first.");
        }
        SiteImage image = new SiteImage();
        image.setContentType(type);
        image.setData(bytes);
        image.setSizeBytes(bytes.length);
        image.setCreatedBy(adminId);
        SiteImage saved = images.save(image);
        events.audit(SERVICE_NAME, "WEBSITE_IMAGE_ADDED", "SiteImage", saved.getId(), adminId,
                Map.of("bytes", bytes.length));
        return SiteImageResponse.of(saved.getId());
    }

    @Transactional(readOnly = true)
    public BrandingService.Image image(long id) {
        SiteImage image = images.findById(id).orElse(null);
        return image == null ? null : new BrandingService.Image(image.getData(), image.getContentType());
    }

    @Transactional
    public void delete(long id, Long adminId) {
        SiteImage image = images.findById(id).orElseThrow(() -> new ResourceNotFoundException("Picture", id));
        images.delete(image);
        events.audit(SERVICE_NAME, "WEBSITE_IMAGE_REMOVED", "SiteImage", id, adminId, Map.of());
    }

    private InstituteSettings load() {
        return settings.findById((short) InstituteSettings.ROW_ID)
                .orElseThrow(() -> new BusinessRuleException("Institute settings have not been initialised"));
    }
}
