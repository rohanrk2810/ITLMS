package com.itilms.placement.dto.response;

import java.time.Instant;

import com.itilms.placement.entity.StageChange;

public record StageChangeResponse(String fromStage, String toStage, Integer roundNo, String note,
                                  Long changedBy, Instant changedAt) {

    public static StageChangeResponse from(StageChange s) {
        return new StageChangeResponse(s.getFromStage() == null ? null : s.getFromStage().name(),
                s.getToStage().name(), s.getRoundNo(), s.getNote(), s.getChangedBy(), s.getChangedAt());
    }
}
