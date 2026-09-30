package com.itilms.identity.dto.response;

import java.time.Instant;

import com.itilms.identity.entity.RefreshToken;

/** One login session: which device, when it started, when it was last seen, how it ended. */
public record SessionResponse(
        String sessionId,
        String device,
        String ipAddress,
        Instant loginTime,
        Instant lastActivity,
        Instant logoutTime,
        /** ACTIVE, LOGGED_OUT, REPLACED (a newer login took over) or EXPIRED. */
        String status) {

    public static SessionResponse from(RefreshToken t) {
        String status;
        if (t.getRevokedAt() == null) {
            status = t.isActive() ? "ACTIVE" : "EXPIRED";
        } else if ("NEW_LOGIN".equals(t.getRevokeReason())) {
            status = "REPLACED";
        } else {
            status = "LOGGED_OUT";
        }
        return new SessionResponse(t.getSessionId(), t.getDeviceLabel(), t.getIpAddress(),
                t.getSessionStartedAt(), t.getLastActivityAt(), t.getRevokedAt(), status);
    }
}
