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
import com.itilms.liveclass.dto.request.FolderRequest;
import com.itilms.liveclass.dto.request.MaterialRequest;
import com.itilms.liveclass.dto.response.FolderResponse;
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
                                       @RequestParam(required = false) String difficulty,
                                       @RequestParam(required = false) String folder,
                                       @RequestParam(required = false) String type) {
        return materialService.list(scope, query, courseId, tag, difficulty, folder, type);
    }

    @Operation(summary = "My folders", description = "With the number of questions filed directly in each.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/folders")
    public List<FolderResponse> folders() {
        return materialService.folders();
    }

    @Operation(summary = "Make a folder", description = "A path such as Java/OOPs/Coding makes every level of it.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping("/folders")
    public ResponseEntity<FolderResponse> createFolder(@Valid @RequestBody FolderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialService.createFolder(request.path()));
    }

    @Operation(summary = "Rename or move a folder", description = "Everything under it moves too.")
    @PreAuthorize(Roles.ACADEMIC)
    @PutMapping("/folders")
    public ResponseEntity<Void> renameFolder(@Valid @RequestBody FolderRequest request) {
        materialService.renameFolder(request.path(), request.to());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a folder",
            description = "Takes every folder inside it and every question filed in them. Cannot be undone.")
    @PreAuthorize(Roles.ACADEMIC)
    @DeleteMapping("/folders")
    public LiveMaterialService.FolderDeleted deleteFolder(@RequestParam String path) {
        return materialService.deleteFolder(path);
    }

    @Operation(summary = "The template for bulk upload",
            description = "Excel by default (headings, example rows and instructions). format=csv gives a zip of the "
                    + "same thing as questions.csv, test-cases.csv and tables.csv.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template(@RequestParam(required = false) String format) throws java.io.IOException {
        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"question-bank-example.json\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(com.itilms.liveclass.service.MaterialJson.example().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        boolean csv = "csv".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\""
                        + (csv ? "question-bank-csv-templates.zip" : "question-bank-template.xlsx") + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(csv ? "application/zip"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(csv ? com.itilms.liveclass.service.MaterialCsv.templateZip()
                        : com.itilms.liveclass.service.MaterialSheet.template());
    }

    @Operation(summary = "The text to give an AI assistant",
            description = "Explains the all-in-one JSON format with an example, so the assistant can write the "
                    + "questions, test cases and tables in a file that can be uploaded as it is.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping(value = "/import/ai-prompt", produces = "text/plain;charset=UTF-8")
    public String aiPrompt() {
        return com.itilms.liveclass.service.MaterialJson.aiPrompt();
    }

    @Operation(summary = "Upload questions",
            description = "One JSON file (the all-in-one format), one Excel file, or the CSV files together (questions, and test cases and tables if used). "
                    + "Every usable row is saved to my bank; rows with a mistake are skipped and reported.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialImportResponse importFiles(
            @RequestParam("file") List<org.springframework.web.multipart.MultipartFile> files) throws java.io.IOException {
        List<com.itilms.liveclass.service.MaterialCsv.NamedFile> named = new java.util.ArrayList<>();
        for (org.springframework.web.multipart.MultipartFile file : files) {
            named.add(new com.itilms.liveclass.service.MaterialCsv.NamedFile(file.getOriginalFilename(), file.getBytes()));
        }
        return materialService.importFiles(named);
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
