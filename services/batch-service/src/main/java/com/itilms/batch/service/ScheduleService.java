package com.itilms.batch.service;

import java.time.LocalDate;
import java.util.List;

import com.itilms.batch.dto.request.CreateSessionRequest;
import com.itilms.batch.dto.request.GenerateScheduleRequest;
import com.itilms.batch.dto.response.SessionResponse;

/** The timetable (Doc S6.7). */
public interface ScheduleService {

    SessionResponse createSession(CreateSessionRequest request);

    SessionResponse updateSession(Long sessionId, CreateSessionRequest request);

    SessionResponse cancelSession(Long sessionId, String reason);

    /**
     * Expands a batch's weekly pattern into individual sessions across a range.
     *
     * <p>Skips the excluded dates and any slot that already exists, so running it
     * twice — or extending a schedule by a month — adds only what is missing
     * rather than failing on the first clash.
     */
    List<SessionResponse> generateSchedule(Long batchId, GenerateScheduleRequest request);

    /**
     * The timetable for whoever is asking.
     *
     * <p>Students see only their own batches, trainers only theirs, staff see
     * everything. The scoping is resolved from the caller's identity rather than
     * from a parameter, so there is no batch id a student can pass to see
     * someone else's schedule (Doc S6.7).
     */
    List<SessionResponse> timetableForCaller(LocalDate from, LocalDate to, Long batchIdFilter);

    List<SessionResponse> sessionsForBatch(Long batchId);

    SessionResponse get(Long sessionId);

    /** Today's classes for the dashboard. */
    List<SessionResponse> today();

    /** Finished classes whose register has not been filled in (Doc S15). */
    List<SessionResponse> pendingAttendance(int lookbackDays);
}
