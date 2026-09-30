package com.itilms.liveclass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.liveclass.entity.LiveQuestion;

public interface LiveQuestionRepository extends JpaRepository<LiveQuestion, Long> {

    List<LiveQuestion> findByLiveSessionIdOrderByOffsetSecondsAscIdAsc(Long liveSessionId);

    List<LiveQuestion> findByLiveSessionIdAndStatus(Long liveSessionId, String status);

    Optional<LiveQuestion> findFirstByLiveSessionIdAndStatusOrderByIdDesc(Long liveSessionId, String status);
}
