package com.itilms.identity.entity;

/**
 * Account state.
 *
 * <p>Doc S6.1 requires that inactive and blocked users cannot sign in. The two
 * are kept separate because they mean different things operationally:
 * INACTIVE is routine (a student finished their course), BLOCKED is deliberate
 * (a disciplinary or security action) and worth seeing in an audit report.
 */
public enum UserStatus {

    ACTIVE,
    INACTIVE,
    BLOCKED;

    public boolean canSignIn() {
        return this == ACTIVE;
    }
}
