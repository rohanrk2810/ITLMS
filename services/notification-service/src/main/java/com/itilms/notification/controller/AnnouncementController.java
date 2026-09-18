package com.itilms.notification.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.dto.PageResponse;
import com.itilms.common.security.Roles;
import com.itilms.notification.dto.NotificationDtos.AnnouncementRequest;
import com.itilms.notification.dto.NotificationDtos.AnnouncementResponse;
import com.itilms.notification.dto.NotificationDtos.AnnouncementUpdateRequest;
import com.itilms.notification.service.AnnouncementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Announcements", description = "Messages from the institute to a group of people")
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcements;

    @Operation(summary = "Announcements",
            description = "Staff see every announcement. Everyone else sees the live ones addressed to them.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public PageResponse<AnnouncementResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return announcements.list(pageable);
    }

    @Operation(summary = "One announcement")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public AnnouncementResponse get(@PathVariable Long id) {
        return announcements.get(id);
    }

    @Operation(summary = "Make an announcement",
            description = "Delivered at once to everyone in the audience. A trainer may address only a batch they teach.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping
    public ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcements.create(request));
    }

    @Operation(summary = "Correct an announcement", description = "Wording and expiry only.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/{id}")
    public AnnouncementResponse update(@PathVariable Long id, @Valid @RequestBody AnnouncementUpdateRequest request) {
        return announcements.update(id, request);
    }

    @Operation(summary = "Withdraw an announcement", description = "Also removes it from everyone's notifications.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/withdraw")
    public AnnouncementResponse withdraw(@PathVariable Long id) {
        return announcements.withdraw(id);
    }
}
