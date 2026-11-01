package com.itilms.certificate.service;

import java.util.List;

import com.itilms.certificate.dto.request.IssueIdCardRequest;
import com.itilms.certificate.dto.response.IdCardResponse;
import com.itilms.certificate.dto.response.IdCardVerificationResponse;
import com.itilms.certificate.entity.IdCardPersonType;
import com.itilms.certificate.entity.IdCardStatus;

/** Identity cards for students, trainers and staff. Issuing, photos and printing are for ADMIN and COORDINATOR. */
public interface IdCardService {

    /** Issues a card. Any card still in force for the same person is revoked as replaced. */
    IdCardResponse issue(IssueIdCardRequest request);

    /** The newest cards, filtered; at most {@link #LIST_LIMIT}. Staff only. */
    List<IdCardResponse> list(IdCardPersonType type, IdCardStatus status, String query);

    /** The caller's own cards. */
    List<IdCardResponse> mine();

    /** One card: staff, or the person it belongs to. */
    IdCardResponse get(Long id);

    /** Stores the photo printed on the card. A JPEG or PNG up to {@link #MAX_PHOTO_BYTES}. */
    IdCardResponse setPhoto(Long id, byte[] bytes, String contentType);

    /** The stored photo, for the screen preview. Staff, or the person it belongs to. */
    Photo photo(Long id);

    /** One card as a PDF, card-sized. */
    byte[] pdf(Long id);

    /** Several cards laid out on A4 sheets to print and cut. Staff only. */
    byte[] sheet(List<Long> ids);

    IdCardResponse revoke(Long id, String reason);

    /** Public: confirm a card by its number and the code printed on it. */
    IdCardVerificationResponse verify(String cardNo, String code);

    int LIST_LIMIT = 300;
    int MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    record Photo(byte[] bytes, String contentType) {
    }
}
