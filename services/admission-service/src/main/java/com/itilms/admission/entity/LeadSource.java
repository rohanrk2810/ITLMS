package com.itilms.admission.entity;

/** Where an enquiry came from. Drives the "which channel actually works" report. */
public enum LeadSource {
    WALK_IN,
    WEBSITE,
    REFERRAL,
    PHONE,
    SOCIAL_MEDIA,
    CAMPAIGN,
    /** Created automatically when a visitor registers themselves on the portal. */
    SELF_REGISTRATION,
    OTHER
}
