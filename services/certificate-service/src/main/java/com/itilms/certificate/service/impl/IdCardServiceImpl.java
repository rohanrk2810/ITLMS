package com.itilms.certificate.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.certificate.client.AdmissionClient;
import com.itilms.certificate.client.BatchClient;
import com.itilms.certificate.config.CertificateProperties;
import com.itilms.certificate.dto.request.IssueIdCardRequest;
import com.itilms.certificate.dto.response.IdCardResponse;
import com.itilms.certificate.dto.response.IdCardVerificationResponse;
import com.itilms.certificate.entity.IdCard;
import com.itilms.certificate.entity.IdCardPersonType;
import com.itilms.certificate.entity.IdCardPhoto;
import com.itilms.certificate.entity.IdCardStatus;
import com.itilms.certificate.repository.IdCardPhotoRepository;
import com.itilms.certificate.repository.IdCardRepository;
import com.itilms.certificate.service.IdCardPdfRenderer;
import com.itilms.certificate.service.IdCardService;
import com.itilms.certificate.service.InstituteBranding;
import com.itilms.certificate.service.VerificationCodes;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdCardServiceImpl implements IdCardService {

    private static final String SERVICE_NAME = "certificate-service";
    private static final int MAX_SHEET_CARDS = 60;

    private final IdCardRepository cards;
    private final IdCardPhotoRepository photos;
    private final AdmissionClient admissionClient;
    private final BatchClient batchClient;
    private final IdCardPdfRenderer renderer;
    private final InstituteBranding branding;
    private final CertificateProperties props;
    private final EventPublisher events;

    @Override
    @Transactional
    public IdCardResponse issue(IssueIdCardRequest request) {
        AppPrincipal issuer = SecurityUtils.requirePrincipal();
        LocalDate today = today();

        IdCard card = IdCard.builder()
                .personType(request.personType())
                .issuedOn(today)
                .issuedBy(issuer.userId())
                .verificationCode(VerificationCodes.generate())
                .build();
        LocalDate defaultUntil = today.plusYears(1);

        switch (request.personType()) {
            case STUDENT -> {
                AdmissionClient.Student student = student(request.personId());
                card.setPersonId(student.id());
                card.setUserId(student.userId());
                card.setPersonCode(student.studentCode());
                card.setFullName(student.fullName());
                card.setPhone(blankToNull(student.phone()));
                card.setDesignation("Student");
                if (request.batchId() != null) {
                    BatchClient.BatchInfo batch = batchClient.batch(request.batchId());
                    if (batch == null) {
                        throw new BusinessRuleException("That batch could not be read. Try again in a moment.");
                    }
                    card.setBatchId(batch.id());
                    card.setBatchName(batch.name());
                    if (batch.endDate() != null && !batch.endDate().isBefore(today)) {
                        defaultUntil = batch.endDate();
                    }
                }
            }
            case TRAINER -> {
                AdmissionClient.Trainer trainer = trainer(request.personId());
                card.setPersonId(trainer.id());
                card.setUserId(trainer.userId());
                card.setPersonCode(trainer.employeeCode());
                card.setFullName(trainer.fullName());
                card.setPhone(blankToNull(trainer.phone()));
                card.setDesignation("Trainer");
            }
            case STAFF -> {
                String name = request.fullName() == null ? "" : request.fullName().trim();
                if (name.isEmpty()) {
                    throw new BusinessRuleException("Type the staff member's name to print on the card.");
                }
                card.setFullName(name);
                card.setPhone(blankToNull(request.phone()));
                String title = request.designation() == null ? "" : request.designation().trim();
                card.setDesignation(title.isEmpty() ? "Staff" : title);
            }
        }

        LocalDate until = request.validUntil() != null ? request.validUntil() : defaultUntil;
        if (until.isBefore(today)) {
            throw new BusinessRuleException("The card cannot be valid until a date that has already passed.");
        }
        card.setValidUntil(until);

        replaceCardsInForce(card, issuer);

        int year = today.getYear();
        card.setCardNo("%s-%d-%04d".formatted(card.getPersonType().prefix(), year, cards.nextSequence()));
        cards.save(card);
        if (card.getPersonType() == IdCardPersonType.STUDENT) {
            copyStudentPhoto(card);
        }

        if (card.getUserId() != null) {
            events.notifyUsers(List.of(card.getUserId()), "ID_CARD", "Your ID card is ready",
                    "ID card %s is ready. You can see and download it from the ID card page.".formatted(card.getCardNo()),
                    "/student/id-card");
        }
        events.audit(SERVICE_NAME, "ID_CARD_ISSUED", "IdCard", card.getId(), null,
                Map.of("cardNo", card.getCardNo(), "personType", card.getPersonType().name(),
                        "name", card.getFullName()));
        return response(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdCardResponse> list(IdCardPersonType type, IdCardStatus status, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        Specification<IdCard> spec = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> all = new ArrayList<>();
            if (type != null) {
                all.add(cb.equal(root.get("personType"), type));
            }
            if (status != null) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (!q.isEmpty()) {
                String like = "%" + q + "%";
                all.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("cardNo")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("personCode"), "")), like)));
            }
            return cb.and(all.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return cards.findAll(spec, PageRequest.of(0, LIST_LIMIT, Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdCardResponse> mine() {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        return cards.findByUserIdOrderByIssuedOnDescIdDesc(caller.userId()).stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IdCardResponse get(Long id) {
        return response(readable(id));
    }

    @Override
    @Transactional
    public IdCardResponse setPhoto(Long id, byte[] bytes, String ignoredClaimedType) {
        IdCard card = require(id);
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("Choose a photo to upload.");
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw new BusinessRuleException("The photo is larger than 2 MB. Use a smaller picture.");
        }
        // The type comes from the bytes, not from what the browser claimed.
        String type = sniff(bytes);
        if (type == null) {
            throw new BusinessRuleException("The photo must be a JPEG or PNG picture.");
        }
        IdCardPhoto photo = photos.findById(id).orElseGet(() -> {
            IdCardPhoto fresh = new IdCardPhoto();
            fresh.setCardId(id);
            return fresh;
        });
        photo.setPhoto(bytes);
        photo.setContentType(type);
        photo.setUpdatedAt(Instant.now());
        photos.save(photo);
        card.setHasPhoto(true);
        cards.save(card);
        events.audit(SERVICE_NAME, "ID_CARD_PHOTO_SET", "IdCard", id, null, Map.of("cardNo", card.getCardNo()));
        return response(card);
    }

    @Override
    @Transactional(readOnly = true)
    public Photo photo(Long id) {
        readable(id);
        IdCardPhoto photo = photos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("This card has no photo yet."));
        return new Photo(photo.getPhoto(), photo.getContentType());
    }

    @Override
    @Transactional
    public byte[] pdf(Long id) {
        IdCard card = readable(id);
        requirePrintable(card);
        byte[] document = renderer.renderOne(card, photoBytes(card), verificationUrl(card));
        events.audit(SERVICE_NAME, "ID_CARD_PRINTED", "IdCard", id, null, Map.of("cardNo", card.getCardNo()));
        return document;
    }

    @Override
    @Transactional
    public byte[] sheet(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessRuleException("Choose at least one card to print.");
        }
        if (ids.size() > MAX_SHEET_CARDS) {
            throw new BusinessRuleException("Print at most %d cards at a time.".formatted(MAX_SHEET_CARDS));
        }
        LocalDate today = today();
        List<IdCardPdfRenderer.Item> items = new ArrayList<>();
        for (IdCard card : cards.findAllById(ids)) {
            if (card.isValid(today)) {
                items.add(new IdCardPdfRenderer.Item(card, photoBytes(card), verificationUrl(card)));
            }
        }
        if (items.isEmpty()) {
            throw new BusinessRuleException("None of the chosen cards can be printed: they are revoked or expired.");
        }
        events.audit(SERVICE_NAME, "ID_CARD_SHEET_PRINTED", "IdCard", ids.get(0), null,
                Map.of("cards", items.size()));
        return renderer.renderSheet(items);
    }

    @Override
    @Transactional
    public IdCardResponse revoke(Long id, String reason) {
        AppPrincipal admin = SecurityUtils.requirePrincipal();
        IdCard card = require(id);
        if (card.getStatus() == IdCardStatus.REVOKED) {
            throw new BusinessRuleException("This card has already been revoked.");
        }
        card.revoke(reason.trim(), admin.userId(), Instant.now());
        cards.save(card);
        if (card.getUserId() != null) {
            events.notifyUsers(List.of(card.getUserId()), "ID_CARD", "ID card withdrawn",
                    "ID card %s was withdrawn: %s".formatted(card.getCardNo(), reason.trim()), "/student/id-card");
        }
        events.audit(SERVICE_NAME, "ID_CARD_REVOKED", "IdCard", id,
                Map.of("status", "ISSUED"), Map.of("status", "REVOKED", "reason", reason.trim()));
        return response(card);
    }

    @Override
    @Transactional(readOnly = true)
    public IdCardVerificationResponse verify(String cardNo, String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("VERIFICATION_CODE_REQUIRED",
                    "Enter the verification code printed on the card.");
        }
        IdCard card = cards.findByCardNo(cardNo.trim().toUpperCase())
                .filter(c -> VerificationCodes.matches(c.getVerificationCode(), code))
                .orElseThrow(() -> new ResourceNotFoundException("No ID card matches that number and code."));
        String status = card.getStatus() == IdCardStatus.REVOKED ? "REVOKED"
                : card.getValidUntil().isBefore(today()) ? "EXPIRED" : "VALID";
        return new IdCardVerificationResponse(card.getCardNo(), status, card.getFullName(),
                card.getPersonType().name(), card.getDesignation(), card.getValidUntil(), branding.get().name());
    }

    // ------------------------------------------------------------------------------------------------

    /** A new card replaces any card still in force for the same person. */
    private void replaceCardsInForce(IdCard fresh, AppPrincipal issuer) {
        List<IdCard> old = fresh.getPersonId() != null
                ? cards.findByPersonTypeAndPersonIdAndStatus(fresh.getPersonType(), fresh.getPersonId(), IdCardStatus.ISSUED)
                : cards.findByPersonTypeAndFullNameIgnoreCaseAndStatusAndPersonIdIsNull(
                        fresh.getPersonType(), fresh.getFullName(), IdCardStatus.ISSUED);
        for (IdCard previous : old) {
            previous.revoke("Replaced by a new card", issuer.userId(), Instant.now());
            cards.save(previous);
        }
    }

    /**
     * A student's card carries the photo they gave at admission, so nobody has to upload it again.
     * It is copied, not linked: the card keeps the face it was printed with until it is reissued.
     */
    private void copyStudentPhoto(IdCard card) {
        var response = admissionClient.studentPhoto(card.getPersonId());
        byte[] bytes = response == null ? null : response.getBody();
        if (bytes == null || bytes.length == 0 || sniff(bytes) == null) {
            return;
        }
        IdCardPhoto photo = new IdCardPhoto();
        photo.setCardId(card.getId());
        photo.setPhoto(bytes);
        photo.setContentType(sniff(bytes));
        photo.setUpdatedAt(Instant.now());
        photos.save(photo);
        card.setHasPhoto(true);
        cards.save(card);
    }

    private AdmissionClient.Student student(Long id) {
        if (id == null) {
            throw new BusinessRuleException("Choose the student.");
        }
        AdmissionClient.Student student = admissionClient.lookup(List.of(id)).stream().findFirst().orElseThrow(() ->
                new BusinessRuleException("That student could not be found, or the admission service did not answer."));
        if ("DROPPED".equals(student.status())) {
            throw new BusinessRuleException("A student who has dropped out cannot be given an ID card.");
        }
        return student;
    }

    private AdmissionClient.Trainer trainer(Long id) {
        if (id == null) {
            throw new BusinessRuleException("Choose the trainer.");
        }
        return admissionClient.lookupTrainers(List.of(id)).stream().findFirst().orElseThrow(() ->
                new BusinessRuleException("That trainer could not be found, or the admission service did not answer."));
    }

    private void requirePrintable(IdCard card) {
        if (card.getStatus() == IdCardStatus.REVOKED) {
            throw new BusinessRuleException("This card has been revoked and cannot be printed.");
        }
        if (card.getValidUntil().isBefore(today())) {
            throw new BusinessRuleException("This card has expired. Issue a new one.");
        }
    }

    private byte[] photoBytes(IdCard card) {
        return card.isHasPhoto() ? photos.findById(card.getId()).map(IdCardPhoto::getPhoto).orElse(null) : null;
    }

    /** Staff may see every card; anyone else only their own. */
    private IdCard readable(Long id) {
        IdCard card = require(id);
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!caller.isStaff() && (card.getUserId() == null || !card.getUserId().equals(caller.userId()))) {
            throw new ForbiddenOperationException("You can only see your own ID card.");
        }
        return card;
    }

    private IdCard require(Long id) {
        return cards.findById(id).orElseThrow(() -> new ResourceNotFoundException("IdCard", id));
    }

    private IdCardResponse response(IdCard card) {
        return IdCardResponse.from(card, verificationUrl(card), today());
    }

    private String verificationUrl(IdCard card) {
        return "%s/%s?code=%s".formatted(props.getIdVerificationBaseUrl(), card.getCardNo(), card.getVerificationCode());
    }

    private LocalDate today() {
        return LocalDate.now(props.getZone());
    }

    /** JPEG or PNG by its first bytes; anything else is refused. */
    static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
