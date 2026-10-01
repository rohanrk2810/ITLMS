package com.itilms.liveclass.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.liveclass.dto.MonitoringDtos.EffectiveResponse;
import com.itilms.liveclass.dto.MonitoringDtos.EventRequest;
import com.itilms.liveclass.dto.MonitoringDtos.EventResponse;
import com.itilms.liveclass.dto.MonitoringDtos.SettingRequest;
import com.itilms.liveclass.dto.MonitoringDtos.SettingResponse;
import com.itilms.liveclass.service.MonitoringService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Student monitoring in live classes. Settings are ADMIN only; a student's browser reports events; the class
 * trainer and staff read them. The service repeats every check.
 */
@Tag(name = "Live class monitoring", description = "Admin-controlled camera monitoring and its event log")
@RestController
@RequestMapping("/api/liveclass")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService service;

    @Operation(summary = "All monitoring settings (admin)")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @GetMapping("/monitoring/settings")
    public List<SettingResponse> settings() {
        return service.listSettings();
    }

    @Operation(summary = "Switch monitoring on or off at one level (admin)",
            description = "Levels: INSTITUTE, COURSE, BATCH, SESSION (the timetable session id). The most specific wins.")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @PutMapping("/monitoring/settings")
    public SettingResponse save(@Valid @RequestBody SettingRequest request) {
        return service.save(request);
    }

    @Operation(summary = "Remove a setting so that level inherits from the one above (admin)")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @DeleteMapping("/monitoring/settings/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "What monitoring applies to this class",
            description = "For a student in the batch or the class's hosts. Monitoring is off unless an admin set it.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/class-sessions/{classSessionId}/monitoring")
    public EffectiveResponse effective(@PathVariable Long classSessionId) {
        return service.effectiveForClass(classSessionId);
    }

    @Operation(summary = "A student's browser reports what its camera check saw")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/class-sessions/{classSessionId}/monitoring/events")
    public ResponseEntity<Void> report(@PathVariable Long classSessionId, @Valid @RequestBody EventRequest request) {
        service.record(classSessionId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "The monitoring log of a class (its trainer, or staff)")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/class-sessions/{classSessionId}/monitoring/events")
    public List<EventResponse> events(@PathVariable Long classSessionId) {
        return service.eventsOf(classSessionId);
    }
}
