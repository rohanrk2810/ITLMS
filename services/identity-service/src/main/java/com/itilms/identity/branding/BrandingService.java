package com.itilms.identity.branding;

import java.time.Instant;
import java.util.List;
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
    /** A campus photo is far bigger than a logo. */
    static final int MAX_BACKGROUND_BYTES = 5 * 1024 * 1024;

    private final InstituteSettingsRepository repository;
    private final EventPublisher events;

    private static final long CACHE_MS = 10_000;
    private volatile Cached cached;

    private record Cached(BrandingResponse value, long at) {
    }

    /** An image the service will serve back to browsers. */
    public record Image(byte[] bytes, String contentType) {
    }

    /**
     * Every page load of every user asks for this, so the row is read from the database at most once in {@link #CACHE_MS}.
     * Any change by the admin clears it at once on this instance; at worst a read racing the commit keeps the old
     * copy for those few seconds. (No transaction here: a cache hit should not take a database connection.)
     */
    public BrandingResponse get() {
        Cached hit = cached;
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.at() < CACHE_MS) {
            return hit.value();
        }
        BrandingResponse fresh = BrandingResponse.from(load());
        cached = new Cached(fresh, now);
        return fresh;
    }

    @Transactional
    public BrandingResponse setStudentMenu(List<String> hidden, Long adminId) {
        InstituteSettings s = load();
        s.setStudentHiddenMenu(String.join(",", hidden.stream().map(String::trim).distinct().toList()));
        return save(s, adminId, "BRANDING_STUDENT_MENU_UPDATED");
    }

    @Transactional
    public BrandingResponse setUiConfig(String json, Long adminId) {
        try {
            if (!new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).isObject()) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new com.itilms.common.exception.BusinessRuleException("INVALID_UI_CONFIG", "The customization is not valid JSON.");
        }
        InstituteSettings s = load();
        s.setUiConfig(json);
        return save(s, adminId, "BRANDING_UI_CONFIG_UPDATED");
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
    public BrandingResponse setLoginBackground(byte[] bytes, Long adminId) {
        InstituteSettings s = load();
        s.setLoginBackgroundType(ImageSniffer.detect(bytes, MAX_BACKGROUND_BYTES));
        s.setLoginBackground(bytes);
        return save(s, adminId, "BRANDING_LOGIN_BACKGROUND_CHANGED");
    }

    @Transactional
    public BrandingResponse clearLoginBackground(Long adminId) {
        InstituteSettings s = load();
        s.setLoginBackground(null);
        s.setLoginBackgroundType(null);
        return save(s, adminId, "BRANDING_LOGIN_BACKGROUND_REMOVED");
    }

    @Transactional(readOnly = true)
    public Image loginBackground() {
        InstituteSettings s = load();
        return s.getLoginBackground() == null ? null : new Image(s.getLoginBackground(), s.getLoginBackgroundType());
    }

    @Transactional
    public BrandingResponse setAppBackground(byte[] bytes, Long adminId) {
        InstituteSettings s = load();
        s.setAppBackgroundType(ImageSniffer.detect(bytes, MAX_BACKGROUND_BYTES));
        s.setAppBackground(bytes);
        return save(s, adminId, "BRANDING_APP_BACKGROUND_CHANGED");
    }

    @Transactional
    public BrandingResponse clearAppBackground(Long adminId) {
        InstituteSettings s = load();
        s.setAppBackground(null);
        s.setAppBackgroundType(null);
        return save(s, adminId, "BRANDING_APP_BACKGROUND_REMOVED");
    }

    @Transactional(readOnly = true)
    public Image appBackground() {
        InstituteSettings s = load();
        return s.getAppBackground() == null ? null : new Image(s.getAppBackground(), s.getAppBackgroundType());
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
        cached = null;
        events.audit(SERVICE_NAME, action, "InstituteSettings", (long) InstituteSettings.ROW_ID, adminId,
                Map.of("name", s.getName()));
        return BrandingResponse.from(s);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
