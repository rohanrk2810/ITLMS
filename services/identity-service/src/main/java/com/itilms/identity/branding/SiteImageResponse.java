package com.itilms.identity.branding;

/** Where to fetch an uploaded website picture. The id is what the website's JSON stores. */
public record SiteImageResponse(long id, String url) {

    static SiteImageResponse of(long id) {
        return new SiteImageResponse(id, "/api/public/branding/site-images/" + id);
    }
}
