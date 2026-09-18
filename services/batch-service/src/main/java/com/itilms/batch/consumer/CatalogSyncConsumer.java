package com.itilms.batch.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.batch.repository.BatchRepository;
import com.itilms.common.event.CoursePublishedEvent;
import com.itilms.common.event.KafkaTopics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the copied course title on each batch current.
 *
 * <p>Batches carry the course title so a timetable can be rendered in one query.
 * When the catalog renames a course, the copies must follow — otherwise a batch
 * listing shows a title the institute stopped using months ago.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogSyncConsumer {

    private final BatchRepository batchRepository;

    @KafkaListener(topics = KafkaTopics.COURSE_PUBLISHED, groupId = "batch-service")
    @Transactional
    public void onCoursePublished(CoursePublishedEvent event) {
        int updated = batchRepository.syncCourse(event.courseId(), event.title(), event.courseCode());
        if (updated > 0) {
            log.debug("Refreshed course title on {} batch(es) for course {}", updated, event.courseId());
        }
    }
}
