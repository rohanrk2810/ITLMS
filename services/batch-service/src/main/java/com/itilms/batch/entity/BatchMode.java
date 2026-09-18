package com.itilms.batch.entity;

/** How a batch meets (Doc S6.6). */
public enum BatchMode {
    ONLINE,
    OFFLINE,
    HYBRID;

    /** True when sessions need a live room provisioning in liveclass-service. */
    public boolean needsLiveRoom() {
        return this == ONLINE || this == HYBRID;
    }
}
