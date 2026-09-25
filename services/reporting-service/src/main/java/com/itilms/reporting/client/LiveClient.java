package com.itilms.reporting.client;

import java.time.Instant;
import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.extern.slf4j.Slf4j;

/** How often a student joined the live classes held for their batches. */
@FeignClient(name = "liveclass-service", fallbackFactory = LiveClient.Fallback.class)
public interface LiveClient {

    @GetMapping("/api/liveclass/internal/students/{studentId}/participation")
    Participation participation(@PathVariable("studentId") Long studentId, @RequestParam("batchIds") List<Long> batchIds);

    record Participation(int sessionsHeld, int sessionsJoined, int minutesInRoom, Integer averageAttendancePercent,
                         Instant lastJoinedAt) {
    }

    @Slf4j
    @Component
    class Fallback implements FallbackFactory<LiveClient> {

        @Override
        public LiveClient create(Throwable cause) {
            return (studentId, batchIds) -> {
                log.warn("liveclass-service unavailable for a progress report: {}", cause.toString());
                return null;
            };
        }
    }
}