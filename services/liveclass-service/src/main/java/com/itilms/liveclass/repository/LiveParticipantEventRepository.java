package com.itilms.liveclass.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.liveclass.entity.LiveParticipantEventLog;

public interface LiveParticipantEventRepository extends JpaRepository<LiveParticipantEventLog, Long> {

    boolean existsByLivekitEventId(String livekitEventId);

    List<LiveParticipantEventLog> findByLiveSessionIdOrderByOccurredAtAsc(Long liveSessionId);
}
