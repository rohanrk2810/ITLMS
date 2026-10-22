package com.itilms.liveclass.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.liveclass.entity.LiveTranscriptLine;

public interface LiveTranscriptLineRepository extends JpaRepository<LiveTranscriptLine, Long> {

    List<LiveTranscriptLine> findByLiveSessionIdOrderBySeqAsc(Long liveSessionId);

    @Query("SELECT COALESCE(MAX(l.seq), 0) FROM LiveTranscriptLine l WHERE l.liveSessionId = :id")
    int highestSeq(@Param("id") Long liveSessionId);

    @Query("SELECT l.clientId FROM LiveTranscriptLine l WHERE l.liveSessionId = :id AND l.clientId IN :clientIds")
    List<String> existingClientIds(@Param("id") Long liveSessionId, @Param("clientIds") Collection<String> clientIds);
}
