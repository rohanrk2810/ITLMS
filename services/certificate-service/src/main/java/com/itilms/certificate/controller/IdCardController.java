package com.itilms.certificate.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.itilms.certificate.dto.request.IssueIdCardRequest;
import com.itilms.certificate.dto.response.IdCardResponse;
import com.itilms.certificate.dto.response.IdCardVerificationResponse;
import com.itilms.certificate.entity.IdCardPersonType;
import com.itilms.certificate.entity.IdCardStatus;
import com.itilms.certificate.service.IdCardService;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.security.Roles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Tag(name = "ID cards", description = "Identity cards for students, trainers and staff")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/id-cards")
@RequiredArgsConstructor
public class IdCardController {

    private final IdCardService service;

    @Operation(summary = "Issue an ID card",
            description = "ADMIN or COORDINATOR. A card still in force for the same person is revoked as replaced.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping
    public ResponseEntity<IdCardResponse> issue(@Valid @RequestBody IssueIdCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(request));
    }

    @Operation(summary = "List ID cards", description = "ADMIN or COORDINATOR. The newest 300, filtered.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping
    public List<IdCardResponse> list(@RequestParam(required = false) IdCardPersonType type,
                                     @RequestParam(required = false) IdCardStatus status,
                                     @RequestParam(required = false) String q) {
        return service.list(type, status, q);
    }

    @Operation(summary = "My ID cards")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public List<IdCardResponse> mine() {
        return service.mine();
    }

    @Operation(summary = "One ID card", description = "Staff, or the person the card belongs to.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public IdCardResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Upload the photo for a card", description = "ADMIN or COORDINATOR. JPEG or PNG up to 2 MB.")
    @PreAuthorize(Roles.STAFF)
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdCardResponse photo(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        try {
            return service.setPhoto(id, file.getBytes(), file.getContentType());
        } catch (java.io.IOException e) {
            throw new BusinessRuleException("The photo could not be read. Try again.");
        }
    }

    @Operation(summary = "The photo of a card", description = "Staff, or the person the card belongs to.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photoOf(@PathVariable Long id) {
        IdCardService.Photo photo = service.photo(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.bytes());
    }

    @Operation(summary = "The card as a PDF", description = "ADMIN or COORDINATOR only; a student or trainer cannot download their own card.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return pdfResponse(service.pdf(id), "id-card-" + id + ".pdf");
    }

    @Operation(summary = "Several cards on A4 sheets", description = "ADMIN or COORDINATOR. Up to 60 cards.")
    @PreAuthorize(Roles.STAFF)
    @GetMapping(value = "/sheet", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> sheet(@RequestParam List<Long> ids) {
        return pdfResponse(service.sheet(ids), "id-cards.pdf");
    }

    @Operation(summary = "Revoke a card", description = "ADMIN or COORDINATOR. The card stays on record and verifies as REVOKED.")
    @PreAuthorize(Roles.STAFF)
    @PostMapping("/{id}/revoke")
    public IdCardResponse revoke(@PathVariable Long id, @Valid @RequestBody RevokeRequest request) {
        return service.revoke(id, request.reason());
    }

    @Operation(summary = "Check an ID card",
            description = "Public. Needs the card number and the code printed on it; shows only the name, role, validity and status.")
    @SecurityRequirements
    @GetMapping("/verify/{cardNo}")
    public IdCardVerificationResponse verify(@PathVariable String cardNo, @RequestParam(required = false) String code) {
        return service.verify(cardNo, code);
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                // A card names a person; it should not sit in a shared cache.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    public record RevokeRequest(@NotBlank(message = "A reason is required") String reason) {
    }
}
