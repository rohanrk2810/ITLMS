package com.itilms.liveclass.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.Roles;
import com.itilms.common.security.SecurityUtils;
import com.itilms.liveclass.dto.request.AskQuestionRequest;
import com.itilms.liveclass.dto.request.MaterialRequest;
import com.itilms.liveclass.dto.response.LiveQuestionResponse;
import com.itilms.liveclass.dto.response.MaterialImportResponse;
import com.itilms.liveclass.dto.response.MaterialResponse;
import com.itilms.liveclass.entity.LiveMaterial;
import com.itilms.liveclass.entity.LiveMaterialFolder;
import com.itilms.liveclass.dto.response.FolderResponse;
import com.itilms.liveclass.repository.LiveMaterialFolderRepository;
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
    private final LiveMaterialFolderRepository folders;
    private final LiveQuestionService questions;
    private final ObjectMapper json;

    /**
     * @param scope  "mine", "shared" (other trainers' shared items) or anything else for both
     * @param query  matches the title, topic, tags and question text
     */
    @Transactional(readOnly = true)
    public List<MaterialResponse> list(String scope, String query, Long courseId, String tag, String difficulty,
                                       String folder, String type) {
        Long me = SecurityUtils.requirePrincipal().userId();
        String needle = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        String wantedFolder = normalizeFolder(folder);
        String wantedTag = tag == null || tag.isBlank() ? null : tag.trim().toLowerCase(Locale.ROOT);
        return materials.findByOwnerUserIdOrSharedTrueOrderByCreatedAtDesc(me).stream()
                .filter(m -> !SCOPE_MINE.equals(scope) || m.getOwnerUserId().equals(me))
                .filter(m -> !SCOPE_SHARED.equals(scope) || !m.getOwnerUserId().equals(me))
                .filter(m -> courseId == null || courseId.equals(m.getCourseId()))
                .filter(m -> difficulty == null || difficulty.isBlank() || difficulty.equalsIgnoreCase(m.getDifficulty()))
                .filter(m -> wantedFolder == null || inFolder(m.getFolder(), wantedFolder))
                .filter(m -> type == null || type.isBlank() || type.equalsIgnoreCase(readQuestion(m.getPayload()).type().name()))
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
        AppPrincipal me = SecurityUtils.requirePrincipal();
        LiveMaterial material = require(id);
        // The owner edits their own; an administrator may also correct one that has been shared.
        boolean allowed = material.getOwnerUserId().equals(me.userId())
                || (Roles.ADMIN.equals(me.role()) && material.isShared());
        if (!allowed) {
            throw new ForbiddenOperationException("This material belongs to another trainer.");
        }
        apply(material, request);
        material.setUpdatedAt(Instant.now());
        return toResponse(materials.save(material), me.userId());
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

    /** Saves every usable row of an Excel sheet to the caller's bank; unusable rows are reported, not fatal. */
    @Transactional
    public MaterialImportResponse importSheet(java.io.InputStream sheet) {
        MaterialSheet.Result parsed;
        try {
            parsed = MaterialSheet.parse(sheet);
        } catch (java.io.IOException | RuntimeException ex) {
            throw new com.itilms.common.exception.BusinessRuleException(
                    "That file could not be read. Upload an .xlsx file made from the template.");
        }
        parsed.items().forEach(this::create);
        return new MaterialImportResponse(parsed.items().size(),
                parsed.errors().stream().map(e -> new MaterialImportResponse.RowError(e.row(), e.message())).toList());
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
                .difficulty(source.getDifficulty()).folder(source.getFolder()).shared(false)
                .build());
        return toResponse(copy, me.userId());
    }

    /** Asks the stored question in the given live class. */
    public LiveQuestionResponse send(Long id, Long liveSessionId) {
        LiveMaterial material = requireVisible(id, SecurityUtils.requirePrincipal().userId());
        return questions.ask(liveSessionId, readQuestion(material.getPayload()));
    }

    // -----------------------------------------------------------------
    // Folders
    // -----------------------------------------------------------------

    /** The caller's folders: those made on purpose and those only implied by a question filed in them. */
    @Transactional(readOnly = true)
    public List<FolderResponse> folders() {
        Long me = SecurityUtils.requirePrincipal().userId();
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        folders.findByOwnerUserId(me).forEach(f -> counts.putIfAbsent(f.getPath(), 0));
        materials.findByOwnerUserId(me).stream().filter(m -> m.getFolder() != null)
                .forEach(m -> counts.merge(m.getFolder(), 1, Integer::sum));
        return counts.entrySet().stream().map(e -> new FolderResponse(e.getKey(), e.getValue())).toList();
    }

    @Transactional
    public FolderResponse createFolder(String path) {
        Long me = SecurityUtils.requirePrincipal().userId();
        String clean = requireFolder(path);
        if (folders.findByOwnerUserIdAndPath(me, clean).isEmpty()) {
            folders.save(LiveMaterialFolder.builder().ownerUserId(me).path(clean).build());
        }
        return new FolderResponse(clean, 0);
    }

    /** Renames a folder and everything under it, or moves it under another (to = "Java/OOPs" for "OOPs"). */
    @Transactional
    public void renameFolder(String from, String to) {
        Long me = SecurityUtils.requirePrincipal().userId();
        String source = requireFolder(from);
        String target = requireFolder(to);
        if (source.equals(target)) {
            return;
        }
        if (inFolder(target, source)) {
            throw new BusinessRuleException("A folder cannot be moved into itself.");
        }
        for (LiveMaterial m : materials.findByOwnerUserId(me)) {
            if (inFolder(m.getFolder(), source)) {
                m.setFolder(target + m.getFolder().substring(source.length()));
                materials.save(m);
            }
        }
        for (LiveMaterialFolder f : folders.findByOwnerUserId(me)) {
            if (inFolder(f.getPath(), source)) {
                String moved = target + f.getPath().substring(source.length());
                if (folders.findByOwnerUserIdAndPath(me, moved).isPresent()) {
                    folders.delete(f);
                } else {
                    f.setPath(moved);
                    folders.save(f);
                }
            }
        }
    }

    /**
     * Deletes a folder with every folder inside it and every question filed in any of them. The caller's own only:
     * a question shared with other trainers goes too, since it lives in this folder. Returns what was removed.
     */
    @Transactional
    public FolderDeleted deleteFolder(String path) {
        Long me = SecurityUtils.requirePrincipal().userId();
        String clean = requireFolder(path);
        List<LiveMaterial> doomedQuestions = materials.findByOwnerUserId(me).stream()
                .filter(m -> inFolder(m.getFolder(), clean)).toList();
        List<LiveMaterialFolder> doomedFolders = folders.findByOwnerUserId(me).stream()
                .filter(f -> inFolder(f.getPath(), clean)).toList();
        materials.deleteAll(doomedQuestions);
        folders.deleteAll(doomedFolders);
        return new FolderDeleted(doomedQuestions.size(), doomedFolders.size());
    }

    /** What deleting a folder took with it. */
    public record FolderDeleted(int questions, int folders) {
    }

    /** "Java/OOPs/ Coding " becomes "Java/OOPs/Coding"; blank is the top level (null). */
    static String normalizeFolder(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> parts = Arrays.stream(raw.split("[/\\\\]")).map(String::trim).filter(p -> !p.isEmpty()).toList();
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() > 6 || parts.stream().anyMatch(p -> p.length() > 60)) {
            throw new BusinessRuleException("A folder path has at most 6 levels, each up to 60 characters.");
        }
        return String.join("/", parts);
    }

    private static String requireFolder(String raw) {
        String clean = normalizeFolder(raw);
        if (clean == null) {
            throw new BusinessRuleException("Give the folder a name.");
        }
        return clean;
    }

    /** True for the folder itself and anything beneath it. */
    static boolean inFolder(String path, String folder) {
        return path != null && (path.equals(folder) || path.startsWith(folder + "/"));
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
        material.setFolder(normalizeFolder(r.folder()));
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
                m.getFolder(), m.getOwnerName(), m.getCreatedAt());
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
