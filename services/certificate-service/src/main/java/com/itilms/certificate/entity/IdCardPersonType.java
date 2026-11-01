package com.itilms.certificate.entity;

/** Whose card it is. The prefix is printed in the card number. */
public enum IdCardPersonType {
    STUDENT("STU"),
    TRAINER("TRN"),
    STAFF("STF");

    private final String prefix;

    IdCardPersonType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
