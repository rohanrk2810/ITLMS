package com.itilms.finance.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.finance.dto.request.FeeTemplateRequest;
import com.itilms.finance.dto.response.FeeTemplateResponse;
import com.itilms.finance.service.FeeTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Fee templates", description = "The usual fee structure of a course")
@RestController
@RequestMapping("/api/fees/templates")
@RequiredArgsConstructor
public class FeeTemplateController {

    private final FeeTemplateService service;

    @Operation(summary = "A course's fee structure", description = "Finance desk, administrator or coordinator.")
    @PreAuthorize(Roles.FINANCE_VIEW)
    @GetMapping("/{courseId}")
    public FeeTemplateResponse get(@PathVariable Long courseId) {
        return service.get(courseId);
    }

    @Operation(summary = "Set a course's fee structure",
            description = "ADMIN or FINANCE. Used for plans raised from now on; plans already raised are not changed.")
    @PreAuthorize(Roles.FINANCE_DESK)
    @PutMapping("/{courseId}")
    public FeeTemplateResponse save(@PathVariable Long courseId, @Valid @RequestBody FeeTemplateRequest request) {
        return service.save(courseId, request);
    }
}
