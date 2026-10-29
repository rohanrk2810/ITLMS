package com.itilms.liveclass.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.liveclass.dto.request.AskQuestionRequest;
import com.itilms.liveclass.dto.request.MaterialRequest;
import com.itilms.liveclass.dto.response.LiveQuestionResponse;
import com.itilms.liveclass.dto.response.MaterialResponse;
import com.itilms.liveclass.entity.LiveMaterial;
import com.itilms.liveclass.repository.LiveMaterialRepository;

import lombok.RequiredArgsConstructor;

/**
 * The question bank. An item belongs to the trainer who made it and only they change it. An owner may share it:
 * other trainers can then find it, send it to their own class and copy it into their bank, but not alter it.
 * Sending goes through {@link LiveQuestionService#ask}, so the usual checks (is this your class, has it ended) apply.
 */
@Service
@RequiredArgsConstructor
public class LiveMaterialService {

    public static final String SCOPE_MINE = "mine";
    public static final String SCOPE_SHARED = "shared";

    private final LiveMaterialRepository materials;
    private final LiveQuestionService questions;
    private final ObjectMapper json;

    /**
     * @param scope  "mine", "shared" (other trainers' shared items) or anything else for both
     * @param query  matches the title, topic, tags and question text
     */
    @Transactional(readOnly = true)
    public List<MaterialResponse> list(String scope, String query, Long courseId, String tag, String difficulty) {
        Long me = SecurityUtils.requirePrincipal().userId();
        String needle = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        String wantedTag = tag == null || tag.isBlank() ? null : tag.trim().toLowerCase(Locale.ROOT);
        return materials.findByOwnerUserIdOrSharedTrueOrderByCreatedAtDesc(me).stream()
                .filter(m -> !SCOPE_MINE.equals(scope) || m.getOwnerUserId().equals(me))
                .filter(m -> !SCOPE_SHARED.equals(scope) || !m.getOwnerUserId().equals(me))
                .filter(m -> courseId == null || courseId.equals(m.getCourseId()))
                .filter(m -> difficulty == null || difficulty.isBlank() || difficulty.equalsIgnoreCase(m.getDifficulty()))
                .filter(m -> wantedTag == null || tagsOf(m).contains(wantedTag))
                .filter(m -> needle == null || haystack(m).contains(needle))
                .map(m -> toResponse(m, me))
                .toList();
    }

    @Transactional
    public MaterialResponse create(MaterialRequest request) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        LiveMaterial material = LiveMaterial.builder().ownerUserId(me.userId()).ownerName(me.fullName()).build();
        apply(material, request);
        return toResponse(materials.save(material), me.userId());
    }

    @Transactional
    public MaterialResponse update(Long id, MaterialRequest request) {
        LiveMaterial material = requireOwned(id);
        apply(material, request);
        material.setUpdatedAt(Instant.now());
        return toResponse(materials.save(material), material.getOwnerUserId());
    }

    @Transactional
    public void delete(Long id) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        LiveMaterial material = require(id);
        // Staff may take down a shared item that should not be circulating.
        if (!material.getOwnerUserId().equals(me.userId()) && !(me.isStaff() && material.isShared())) {
            throw new ForbiddenOperationException("This material belongs to another trainer.");
        }
        materials.delete(material);
    }

    /** Puts a copy of a visible item into the caller's own bank, private until they share it. */
    @Transactional
    public MaterialResponse copy(Long id) {
        AppPrincipal me = SecurityUtils.requirePrincipal();
        LiveMaterial source = requireVisible(id, me.userId());
        LiveMaterial copy = materials.save(LiveMaterial.builder()
                .ownerUserId(me.userId()).ownerName(me.fullName())
                .title(source.getTitle()).payload(source.getPayload())
                .courseId(source.getCourseId()).topic(source.getTopic()).tags(source.getTags())
                .difficulty(source.getDifficulty()).shared(false)
                .build());
        return toResponse(copy, me.userId());
    }

    /** Asks the stored question in the given live class. */
    public LiveQuestionResponse send(Long id, Long liveSessionId) {
        LiveMaterial material = requireVisible(id, SecurityUtils.requirePrincipal().userId());
        return questions.ask(liveSessionId, readQuestion(material.getPayload()));
    }

    private void apply(LiveMaterial material, MaterialRequest r) {
        material.setTitle(r.title().trim());
        material.setPayload(write(r.question()));
        material.setCourseId(r.courseId());
        material.setTopic(blankToNull(r.topic()));
        material.setTags(r.tags() == null ? null : blankToNull(String.join(",", r.tags().stream()
                .map(t -> t == null ? "" : t.trim().toLowerCase(Locale.ROOT).replace(",", " "))
                .filter(t -> !t.isEmpty()).distinct().toList())));
        material.setDifficulty(blankToNull(r.difficulty()));
        material.setShared(Boolean.TRUE.equals(r.shared()));
    }

    private LiveMaterial require(Long id) {
        return materials.findById(id).orElseThrow(() -> new ResourceNotFoundException("Material", id));
    }

    private LiveMaterial requireOwned(Long id) {
        LiveMaterial material = require(id);
        if (!material.getOwnerUserId().equals(SecurityUtils.requirePrincipal().userId())) {
            throw new ForbiddenOperationException("This material belongs to another trainer.");
        }
        return material;
    }

    private LiveMaterial requireVisible(Long id, Long me) {
        LiveMaterial material = require(id);
        if (!material.getOwnerUserId().equals(me) && !material.isShared()) {
            throw new ForbiddenOperationException("This material belongs to another trainer.");
        }
        return material;
    }

    private static List<String> tagsOf(LiveMaterial m) {
        return m.getTags() == null ? List.of() : Arrays.asList(m.getTags().split(","));
    }

    private String haystack(LiveMaterial m) {
        return String.join(" ", m.getTitle(), m.getTopic() == null ? "" : m.getTopic(),
                m.getTags() == null ? "" : m.getTags(), m.getPayload()).toLowerCase(Locale.ROOT);
    }

    private MaterialResponse toResponse(LiveMaterial m, Long me) {
        return new MaterialResponse(m.getId(), m.getTitle(), readQuestion(m.getPayload()), m.getCourseId(),
                m.getTopic(), tagsOf(m), m.getDifficulty(), m.isShared(), m.getOwnerUserId().equals(me),
                m.getOwnerName(), m.getCreatedAt());
    }

    private AskQuestionRequest readQuestion(String payload) {
        try {
            return json.readValue(payload, AskQuestionRequest.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored material is not valid JSON", ex);
        }
    }

    private String write(AskQuestionRequest question) {
        try {
            return json.writeValueAsString(question);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
