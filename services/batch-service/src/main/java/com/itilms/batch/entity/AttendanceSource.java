package com.itilms.batch.entity;

/** Where an attendance record came from. */
public enum AttendanceSource {
    /** A trainer marked the register. */
    MANUAL,
    /** Derived from time spent in a LiveKit room. */
    LIVE_CLASS,
    /** Bulk loaded from a spreadsheet during migration. */
    IMPORT
}
