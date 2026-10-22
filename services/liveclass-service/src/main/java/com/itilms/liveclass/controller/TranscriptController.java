package com.itilms.liveclass.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.liveclass.service.TranscriptService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Subtitles and the written record of what the trainer said. */
@Tag(name = "Class transcript", description = "Saving and downloading what the trainer said in a class")
@RestController
@RequestMapping("/api/liveclass/sessions/{id}/transcript")
@RequiredArgsConstructor
public class TranscriptController {

    private final TranscriptService transcripts;

    public record PhraseRequest(@Size(max = 40) String clientId, @NotNull @Size(max = 1000) String text,
                                @Size(max = 10) String language, Long spokenAt) {
    }

    public record PhrasesRequest(@NotNull @Size(max = 40) List<@Valid PhraseRequest> phrases) {
    }

    @Operation(summary = "Save what the trainer has just said",
            description = "Finished phrases from the class's trainer (or staff), while the class is live. A phrase that was "
                    + "already saved is skipped, so a retry is safe. Words only: no audio is ever sent here.")
    @PreAuthorize(Roles.ACADEMIC)
    @PostMapping
    public Map<String, Integer> save(@PathVariable Long id, @Valid @RequestBody PhrasesRequest request) {
        List<TranscriptService.Incoming> incoming = request.phrases().stream()
                .map(p -> new TranscriptService.Incoming(p.clientId(), p.text(), p.language(), p.spokenAt())).toList();
        return Map.of("saved", transcripts.append(id, incoming));
    }

    @Operation(summary = "Download the class transcript",
            description = "A text file. The class's trainer, staff, or a student enrolled in the batch. 404 when nothing was captured.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        TranscriptService.TranscriptFile file = transcripts.file(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content().getBytes(StandardCharsets.UTF_8));
    }
}
