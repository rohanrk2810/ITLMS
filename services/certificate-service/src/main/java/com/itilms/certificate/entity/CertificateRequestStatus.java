package com.itilms.certificate.entity;

/** Where a certificate request stands. */
public enum CertificateRequestStatus {
    PENDING,
    /** Accepted by an admin; the certificate itself is not yet issued. */
    APPROVED,
    REJECTED,
    /** A certificate exists. Set only in the same transaction that creates it. */
    ISSUED;

    /** Still in play: blocks a duplicate request for the same course. */
    public boolean isOpen() {
        return this == PENDING || this == APPROVED;
    }
}
