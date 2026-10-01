package com.itilms.liveclass.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.liveclass.entity.MonitoringEvent;

public interface MonitoringEventRepository extends JpaRepository<MonitoringEvent, Long> {

    List<MonitoringEvent> findByLiveSessionIdOrderByOccurredAtAscIdAsc(Long liveSessionId);

    /** Used to cap what one student can write into one class. */
    long countByLiveSessionIdAndStudentId(Long liveSessionId, Long studentId);
}
