package com.itilms.identity.branding;

/** What anyone may see: no image bytes, just where to fetch them. */
public record BrandingResponse(
        String name,
        String tagline,
        String primaryColor,
        String contactEmail,
        String contactPhone,
        String website,
        String address,
        String signatoryName,
        String signatoryTitle,
        String logoUrl,
        String faviconUrl,
        long version) {

    static BrandingResponse from(InstituteSettings s) {
        String v = "?v=" + s.getVersion();
        return new BrandingResponse(s.getName(), s.getTagline(), s.getPrimaryColor(), s.getContactEmail(),
                s.getContactPhone(), s.getWebsite(), s.getAddress(), s.getSignatoryName(), s.getSignatoryTitle(),
                s.getLogo() == null ? null : "/api/public/branding/logo" + v,
                s.getFavicon() == null ? null : "/api/public/branding/favicon" + v,
                s.getVersion());
    }
}
