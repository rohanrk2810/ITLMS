package com.itilms.identity.branding;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The institute's name and look. Reading is anonymous; every write is ADMIN only. */
@Tag(name = "Branding", description = "Institute name, logo and colours")
@RestController
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService service;

    @SecurityRequirements
    @GetMapping("/api/public/branding")
    public BrandingResponse get() {
        return service.get();
    }

    @SecurityRequirements
    @GetMapping("/api/public/branding/logo")
    public ResponseEntity<byte[]> logo() {
        return image(service.logo());
    }

    @SecurityRequirements
    @GetMapping("/api/public/branding/favicon")
    public ResponseEntity<byte[]> favicon() {
        return image(service.favicon());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/api/branding")
    public BrandingResponse update(@Valid @RequestBody UpdateBrandingRequest request,
                                   @AuthenticationPrincipal AppPrincipal admin) {
        return service.update(request, admin.userId());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/api/branding/logo")
    public BrandingResponse uploadLogo(@RequestPart("file") MultipartFile file,
                                       @AuthenticationPrincipal AppPrincipal admin) throws IOException {
        return service.setLogo(file.getBytes(), admin.userId());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/api/branding/favicon")
    public BrandingResponse uploadFavicon(@RequestPart("file") MultipartFile file,
                                          @AuthenticationPrincipal AppPrincipal admin) throws IOException {
        return service.setFavicon(file.getBytes(), admin.userId());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @DeleteMapping("/api/branding/logo")
    public BrandingResponse removeLogo(@AuthenticationPrincipal AppPrincipal admin) {
        return service.clearLogo(admin.userId());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @DeleteMapping("/api/branding/favicon")
    public BrandingResponse removeFavicon(@AuthenticationPrincipal AppPrincipal admin) {
        return service.clearFavicon(admin.userId());
    }

    private static ResponseEntity<byte[]> image(BrandingService.Image image) {
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                // The URL carries ?v=version, so a changed image is a new URL and can cache long.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
