package com.itilms.identity.branding;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrandingService {

    private static final String SERVICE_NAME = "identity-service";
    static final int MAX_IMAGE_BYTES = 1024 * 1024;

    private final InstituteSettingsRepository repository;
    private final EventPublisher events;

    /** An image the service will serve back to browsers. */
    public record Image(byte[] bytes, String contentType) {
    }

    @Transactional(readOnly = true)
    public BrandingResponse get() {
        return BrandingResponse.from(load());
    }

    @Transactional
    public BrandingResponse update(UpdateBrandingRequest r, Long adminId) {
        InstituteSettings s = load();
        s.setName(r.name().trim());
        s.setTagline(blankToNull(r.tagline()));
        s.setPrimaryColor(blankToNull(r.primaryColor()));
        s.setContactEmail(blankToNull(r.contactEmail()));
        s.setContactPhone(blankToNull(r.contactPhone()));
        s.setWebsite(blankToNull(r.website()));
        s.setAddress(blankToNull(r.address()));
        s.setSignatoryName(blankToNull(r.signatoryName()));
        s.setSignatoryTitle(blankToNull(r.signatoryTitle()));
        return save(s, adminId, "BRANDING_UPDATED");
    }

    @Transactional
    public BrandingResponse setLogo(byte[] bytes, Long adminId) {
        InstituteSettings s = load();
        s.setLogoType(ImageSniffer.detect(bytes));
        s.setLogo(bytes);
        return save(s, adminId, "BRANDING_LOGO_CHANGED");
    }

    @Transactional
    public BrandingResponse setFavicon(byte[] bytes, Long adminId) {
        InstituteSettings s = load();
        s.setFaviconType(ImageSniffer.detect(bytes));
        s.setFavicon(bytes);
        return save(s, adminId, "BRANDING_FAVICON_CHANGED");
    }

    @Transactional
    public BrandingResponse clearLogo(Long adminId) {
        InstituteSettings s = load();
        s.setLogo(null);
        s.setLogoType(null);
        return save(s, adminId, "BRANDING_LOGO_REMOVED");
    }

    @Transactional
    public BrandingResponse clearFavicon(Long adminId) {
        InstituteSettings s = load();
        s.setFavicon(null);
        s.setFaviconType(null);
        return save(s, adminId, "BRANDING_FAVICON_REMOVED");
    }

    @Transactional(readOnly = true)
    public Image logo() {
        InstituteSettings s = load();
        return s.getLogo() == null ? null : new Image(s.getLogo(), s.getLogoType());
    }

    @Transactional(readOnly = true)
    public Image favicon() {
        InstituteSettings s = load();
        return s.getFavicon() == null ? null : new Image(s.getFavicon(), s.getFaviconType());
    }

    private InstituteSettings load() {
        return repository.findById((short) InstituteSettings.ROW_ID)
                .orElseThrow(() -> new BusinessRuleException("Institute settings have not been initialised"));
    }

    private BrandingResponse save(InstituteSettings s, Long adminId, String action) {
        s.setVersion(s.getVersion() + 1);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(adminId);
        repository.save(s);
        events.audit(SERVICE_NAME, action, "InstituteSettings", (long) InstituteSettings.ROW_ID, adminId,
                Map.of("name", s.getName()));
        return BrandingResponse.from(s);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
