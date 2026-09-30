package com.itilms.liveclass.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.liveclass.entity.LiveQuestion;

public interface LiveQuestionRepository extends JpaRepository<LiveQuestion, Long> {

    List<LiveQuestion> findByLiveSessionIdOrderByOffsetSecondsAscIdAsc(Long liveSessionId);

    List<LiveQuestion> findByLiveSessionIdAndStatus(Long liveSessionId, String status);

    Optional<LiveQuestion> findFirstByLiveSessionIdAndStatusOrderByIdDesc(Long liveSessionId, String status);

    /**
     * Closes whatever question was still open when the class ended - by the trainer's End button, or by the sweep
     * settling a room nobody closed by hand. A recording has no "open" question to push updates to, so leaving one
     * open forever would strand it; a student reviewing later just sees every question as one to answer from the
     * recording.
     */
    @Modifying
    @Transactional
    @Query("UPDATE LiveQuestion q SET q.status = 'CLOSED', q.closedAt = :now WHERE q.liveSessionId = :liveSessionId AND q.status = 'OPEN'")
    int closeAllOpen(@Param("liveSessionId") Long liveSessionId, @Param("now") Instant now);
}
