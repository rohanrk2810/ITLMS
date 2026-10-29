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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.liveclass.dto.request.MaterialRequest;
import com.itilms.liveclass.dto.response.LiveQuestionResponse;
import com.itilms.liveclass.dto.response.MaterialImportResponse;
import com.itilms.liveclass.dto.response.MaterialResponse;
import com.itilms.liveclass.service.LiveMaterialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** A trainer's prepared lecture material: questions, code and small databases kept to send to a class. */
@Tag(name = "Lecture material", description = "Questions prepared ahead of a live class")
@RestController
@RequestMapping("/api/liveclass/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final LiveMaterialService materialService;

    @Operation(summary = "The question bank",
            description = "My own items and the ones other trainers shared. scope=mine or shared narrows it; "
                    + "query searches title, topic, tags and question text.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping
    public List<MaterialResponse> list(@RequestParam(required = false) String scope,
                                       @RequestParam(required = false) String query,
                                       @RequestParam(required = false) Long courseId,
                                       @RequestParam(required = false) String tag,
                                       @RequestParam(required = false) String difficulty) {
        return materialService.list(scope, query, courseId, tag, difficulty);
    }

    @Operation(summary = "The Excel template for bulk upload",
            description = "Headings, three example rows and a page of instructions.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() throws java.io.IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"question-bank-template.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(com.itilms.liveclass.service.MaterialSheet.template());
    }

    @Operation(summary = "Upload an Excel sheet of questions",
            description = "Every usable row is saved to my bank. Rows with a mistake are skipped and reported.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialImportResponse importSheet(@RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        return materialService.importSheet(file.getInputStream());
    }

    @Operation(summary = "Copy a shared item into my bank")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/copy")
    public ResponseEntity<MaterialResponse> copy(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialService.copy(id));
    }

    @Operation(summary = "Save lecture material")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping
    public ResponseEntity<MaterialResponse> create(@Valid @RequestBody MaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialService.create(request));
    }

    @Operation(summary = "Change lecture material")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/{id}")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody MaterialRequest request) {
        return materialService.update(id, request);
    }

    @Operation(summary = "Delete lecture material")
    @PreAuthorize(Roles.ACADEMIC)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send material to a live class",
            description = "Asks the saved question in the class, exactly as if it had been typed there.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/{id}/send")
    public LiveQuestionResponse send(@PathVariable Long id, @RequestParam Long liveSessionId) {
        return materialService.send(id, liveSessionId);
    }
}
