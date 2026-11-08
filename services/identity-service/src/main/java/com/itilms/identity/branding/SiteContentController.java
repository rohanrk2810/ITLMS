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
import org.springframework.web.bind.annotation.PathVariable;
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

/** The public website's wording and pictures. Reading is anonymous; every write is ADMIN only. */
@Tag(name = "Website", description = "Public front page content and pictures")
@RestController
@RequiredArgsConstructor
public class SiteContentController {

    private final SiteContentService service;

    @SecurityRequirements
    @GetMapping("/api/public/branding/site")
    public ResponseEntity<SiteConfigResponse> get() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(15)).cachePublic())
                .body(service.get());
    }

    @SecurityRequirements
    @GetMapping("/api/public/branding/site-images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        BrandingService.Image image = service.image(id);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                // A picture's bytes never change under its id, so it can be cached for good.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/api/branding/site-config")
    public SiteConfigResponse set(@Valid @RequestBody SiteConfigRequest request,
                                  @AuthenticationPrincipal AppPrincipal admin) {
        return service.set(request.config(), admin.userId());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @PostMapping("/api/branding/site-images")
    public SiteImageResponse upload(@RequestPart("file") MultipartFile file,
                                    @AuthenticationPrincipal AppPrincipal admin) throws IOException {
        return service.upload(file.getBytes(), admin.userId());
    }

    @PreAuthorize(Roles.ADMIN_ONLY)
    @DeleteMapping("/api/branding/site-images/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal AppPrincipal admin) {
        service.delete(id, admin.userId());
        return ResponseEntity.noContent().build();
    }
}
