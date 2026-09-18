package com.itilms.certificate.entity;

/** Whether a certificate still stands. */
public enum CertificateStatus {
    ISSUED,
    /**
     * Withdrawn - issued in error, or found to rest on a record later
     * corrected. Kept, not deleted: the public verification page must be able
     * to say "this certificate was revoked", not "never heard of it".
     */
    REVOKED
}
