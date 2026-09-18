package com.itilms.admission.entity;

/** Whether a trainer is currently available for batch allocation. */
public enum TrainerStatus {
    ACTIVE,
    INACTIVE,
    ON_LEAVE;

    public boolean isAvailableForAllocation() {
        return this == ACTIVE;
    }
}
