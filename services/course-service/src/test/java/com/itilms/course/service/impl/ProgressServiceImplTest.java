package com.itilms.course.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.CourseProgressUpdatedEvent;
import com.itilms.common.event.EnrollmentCreatedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.course.dto.request.LessonProgressRequest;
import com.itilms.course.dto.response.ProgressResponse;
import com.itilms.course.entity.CourseEnrollment;
import com.itilms.course.entity.EnrollmentStatus;
import com.itilms.course.entity.LessonProgress;
import com.itilms.course.repository.CourseEnrollmentRepository;
import com.itilms.course.repository.CourseModuleRepository;
import com.itilms.course.repository.CourseRepository;
import com.itilms.course.repository.LessonProgressRepository;
import com.itilms.course.repository.LessonRepository;

/**
 * Lesson progress feeds the certificate's first completion rule, so the arithmetic (mandatory lessons
 * only) and the access rules (own enrolment only) are pinned here.
 */
@ExtendWith(MockitoExtension.class)
class ProgressServiceImplTest {

    private static final long COURSE = 3L;
    private static final long LESSON = 30L;

    @Mock CourseRepository courseRepository;
    @Mock LessonRepository lessonRepository;
    @Mock CourseEnrollmentRepository enrollmentRepository;
    @Mock LessonProgressRepository progressRepository;
    @Mock EventPublisher events;
    @Mock CourseModuleRepository moduleRepository;

    ProgressServiceImpl service;
    CourseEnrollment enrollment;

    @BeforeEach
    void setUp() {
        service = new ProgressServiceImpl(courseRepository, lessonRepository, enrollmentRepository, progressRepository, events,
                moduleRepository);
        enrollment = CourseEnrollment.builder().id(60L).enrollmentId(5L).studentId(10L).userId(1L).courseId(COURSE).batchId(7L).build();

        lenient().when(lessonRepository.existsById(LESSON)).thenReturn(true);
        lenient().when(courseRepository.findCourseIdByLessonId(LESSON)).thenReturn(Optional.of(COURSE));
        lenient().when(enrollmentRepository.findByStudentIdAndCourseId(10L, COURSE)).thenReturn(Optional.of(enrollment));
        lenient().when(progressRepository.findByEnrollmentIdAndLessonId(60L, LESSON)).thenReturn(Optional.empty());
        lenient().when(progressRepository.save(any(LessonProgress.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(lessonRepository.findMandatoryLessonIds(COURSE)).thenReturn(List.of(30L, 31L));
        actAs("STUDENT", 10L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void actAs(String role, Long profileId) {
        AppPrincipal principal = new AppPrincipal(1L, "u@x", "User", role, profileId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static LessonProgressRequest done() {
        return new LessonProgressRequest(120, true);
    }

    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("The report breaks a course into modules, showing which are unfinished, and counts watched videos")
    void courseProgressShowsUnfinishedModulesAndVideos() {
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setProgressPercent(java.math.BigDecimal.valueOf(50));
        enrollment.setCompletedLessons(2);
        enrollment.setTotalLessons(4);
        when(enrollmentRepository.findByStudentId(10L)).thenReturn(List.of(enrollment));
        when(courseRepository.findById(COURSE)).thenReturn(Optional.of(
                com.itilms.course.entity.Course.builder().id(COURSE).title("Java").build()));
        when(moduleRepository.findByCourseIdOrderBySequenceNo(COURSE)).thenReturn(List.of(
                com.itilms.course.entity.CourseModule.builder().id(1L).courseId(COURSE).title("Basics").sequenceNo(1).build(),
                com.itilms.course.entity.CourseModule.builder().id(2L).courseId(COURSE).title("OOP").sequenceNo(2).build()));
        when(lessonRepository.findByModuleIdInOrderByModuleIdAscSequenceNoAsc(List.of(1L, 2L))).thenReturn(List.of(
                lesson(10L, 1L, com.itilms.course.entity.LessonType.VIDEO, true),
                lesson(11L, 1L, com.itilms.course.entity.LessonType.NOTE, true),
                lesson(20L, 2L, com.itilms.course.entity.LessonType.VIDEO, true),
                lesson(21L, 2L, com.itilms.course.entity.LessonType.VIDEO, false)));
        when(progressRepository.findCompletedLessonIds(60L)).thenReturn(List.of(10L, 11L, 21L));

        var courses = service.courseProgressOf(10L);

        assertThat(courses).singleElement().satisfies(c -> {
            assertThat(c.courseTitle()).isEqualTo("Java");
            assertThat(c.progressPercent()).isEqualTo(50);
            assertThat(c.videoLessons()).isEqualTo(3);
            assertThat(c.videoLessonsCompleted()).isEqualTo(2);   // lessons 10 and 21
            assertThat(c.modules()).extracting(m -> m.title() + ":" + m.completed() + "/" + m.lessons())
                    .containsExactly("Basics:2/2", "OOP:0/1");    // 21 is optional, so it is not part of the module
            assertThat(c.modules().get(0).isComplete()).isTrue();
            assertThat(c.modules().get(1).isComplete()).isFalse();
        });
    }

    private static com.itilms.course.entity.Lesson lesson(Long id, Long moduleId, com.itilms.course.entity.LessonType type,
                                                          boolean mandatory) {
        return com.itilms.course.entity.Lesson.builder().id(id).moduleId(moduleId).type(type).mandatory(mandatory).build();
    }

    @Nested
    @DisplayName("Recording progress")
    class Recording {

        @Test
        void finishingOneOfTwoMandatoryLessonsIsHalfWayAndSaysSo() {
            when(progressRepository.countCompletedAmong(eq(60L), anyList())).thenReturn(1);

            ProgressResponse response = service.recordProgress(LESSON, done());

            assertThat(response.progressPercent()).isEqualByComparingTo("50.00");
            assertThat(response.completedLessons()).isEqualTo(1);
            assertThat(response.totalLessons()).isEqualTo(2);
            ArgumentCaptor<CourseProgressUpdatedEvent> event = ArgumentCaptor.forClass(CourseProgressUpdatedEvent.class);
            verify(events).publishAfterCommit(eq(KafkaTopics.COURSE_PROGRESS_UPDATED), event.capture());
            assertThat(event.getValue().allMandatoryLessonsComplete()).isFalse();
            verify(events, never()).notifyUsers(any(), anyString(), anyString(), anyString(), any());
        }

        @Test
        void theLastMandatoryLessonCompletesTheCourseAndCongratulatesTheStudentOnce() {
            when(progressRepository.countCompletedAmong(eq(60L), anyList())).thenReturn(2);

            service.recordProgress(LESSON, done());

            assertThat(enrollment.allLessonsComplete()).isTrue();
            assertThat(enrollment.getCompletedAt()).isNotNull();
            verify(events).notifyUsers(eq(List.of(1L)), eq("COURSE_PROGRESS"), anyString(), anyString(), any());

            // Recording another lesson afterwards must not congratulate them a second time.
            service.recordProgress(LESSON, new LessonProgressRequest(300, null));
            verify(events, org.mockito.Mockito.times(1)).notifyUsers(any(), anyString(), anyString(), anyString(), any());
        }

        @Test
        void aCourseWithNoMandatoryLessonsNeverReadsAsComplete() {
            when(lessonRepository.findMandatoryLessonIds(COURSE)).thenReturn(List.of());

            ProgressResponse response = service.recordProgress(LESSON, done());

            assertThat(response.progressPercent()).isEqualByComparingTo("0");
            assertThat(enrollment.allLessonsComplete()).isFalse();
            verify(progressRepository, never()).countCompletedAmong(anyLong(), anyList());
        }

        @Test
        void aLessonCanBeUnmarkedAndThenTheCourseIsNoLongerComplete() {
            enrollment.recalculate(2, 2);
            when(progressRepository.countCompletedAmong(eq(60L), anyList())).thenReturn(1);
            LessonProgress existing = LessonProgress.builder().enrollmentId(60L).lessonId(LESSON).build();
            existing.markComplete();
            when(progressRepository.findByEnrollmentIdAndLessonId(60L, LESSON)).thenReturn(Optional.of(existing));

            service.recordProgress(LESSON, new LessonProgressRequest(null, false));

            assertThat(existing.isCompleted()).isFalse();
            assertThat(enrollment.allLessonsComplete()).isFalse();
            assertThat(enrollment.getCompletedAt()).isNull();
        }

        @Test
        void onlyAStudentWithAProfileMayRecordProgress() {
            actAs("TRAINER", 20L);
            assertThatThrownBy(() -> service.recordProgress(LESSON, done())).isInstanceOf(ForbiddenOperationException.class);

            actAs("STUDENT", null);
            assertThatThrownBy(() -> service.recordProgress(LESSON, done())).isInstanceOf(ForbiddenOperationException.class);
            verify(progressRepository, never()).save(any());
        }

        @Test
        void aLessonThatDoesNotExistIsNotFound() {
            assertThatThrownBy(() -> service.recordProgress(999L, done())).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void aStudentNotEnrolledOnThatCourseIsRefused() {
            when(enrollmentRepository.findByStudentIdAndCourseId(10L, COURSE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordProgress(LESSON, done()))
                    .isInstanceOf(ForbiddenOperationException.class).hasMessageContaining("not enrolled");
        }

        @Test
        void aDroppedOrSuspendedEnrolmentCannotRecordProgress() {
            for (EnrollmentStatus status : List.of(EnrollmentStatus.DROPPED, EnrollmentStatus.SUSPENDED, EnrollmentStatus.COMPLETED)) {
                enrollment.setStatus(status);
                assertThatThrownBy(() -> service.recordProgress(LESSON, done()))
                        .as(status.name()).isInstanceOf(BusinessRuleException.class);
            }
            verify(progressRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Reading progress")
    class Reading {

        @Test
        void aStudentCanReadOnlyTheirOwnProgress() {
            assertThat(service.progressFor(10L, COURSE).studentId()).isEqualTo(10L);

            assertThatThrownBy(() -> service.progressFor(11L, COURSE)).isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void staffCanReadAnyStudentsProgressAndAMissingEnrolmentIsNotFound() {
            actAs("ADMIN", null);
            when(enrollmentRepository.findByStudentIdAndCourseId(11L, COURSE)).thenReturn(Optional.empty());

            assertThat(service.progressFor(10L, COURSE)).isNotNull();
            assertThatThrownBy(() -> service.progressFor(11L, COURSE)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Keeping in step with other services")
    class Syncing {

        private EnrollmentCreatedEvent created() {
            return new EnrollmentCreatedEvent("ev-1", Instant.now(), 5L, 10L, 1L, COURSE, 7L, "JFS-1");
        }

        @Test
        void aNewEnrolmentIsMirroredWithTheCurrentMandatoryLessonCount() {
            when(enrollmentRepository.existsByEnrollmentId(5L)).thenReturn(false);
            when(courseRepository.existsById(COURSE)).thenReturn(true);
            when(courseRepository.countMandatoryLessons(COURSE)).thenReturn(6);
            when(enrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(i -> i.getArgument(0));

            service.mirrorEnrollment(created());

            ArgumentCaptor<CourseEnrollment> saved = ArgumentCaptor.forClass(CourseEnrollment.class);
            verify(enrollmentRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(saved.getValue().getTotalLessons()).isEqualTo(6);
        }

        @Test
        void aRedeliveredEventOrAnUnknownCourseChangesNothing() {
            when(enrollmentRepository.existsByEnrollmentId(5L)).thenReturn(true);
            service.mirrorEnrollment(created());

            when(enrollmentRepository.existsByEnrollmentId(5L)).thenReturn(false);
            when(courseRepository.existsById(COURSE)).thenReturn(false);
            service.mirrorEnrollment(created());

            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        void closingAnEnrolmentSetsItsStatusAndAnUnknownEnrolmentIsIgnored() {
            when(enrollmentRepository.findByEnrollmentId(5L)).thenReturn(Optional.of(enrollment));
            when(enrollmentRepository.findByEnrollmentId(404L)).thenReturn(Optional.empty());

            service.closeEnrollment(5L, "dropped");
            service.closeEnrollment(404L, "DROPPED");

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.DROPPED);
            verify(enrollmentRepository).save(enrollment);
        }

        @Test
        void aCurriculumEditRecalculatesEveryActiveEnrolmentWithoutFloodingStudentsWithEvents() {
            CourseEnrollment other = CourseEnrollment.builder().id(61L).enrollmentId(6L).studentId(11L).userId(2L).courseId(COURSE).build();
            when(enrollmentRepository.findActiveByCourseId(COURSE)).thenReturn(List.of(enrollment, other));
            when(progressRepository.countCompletedAmong(anyLong(), anyList())).thenReturn(1);

            service.recalculateCourse(COURSE);

            assertThat(enrollment.getTotalLessons()).isEqualTo(2);
            assertThat(other.getProgressPercent()).isEqualByComparingTo("50.00");
            verify(events, never()).publishAfterCommit(anyString(), any());
            verify(events, never()).notifyUsers(any(), anyString(), anyString(), anyString(), any());
        }
    }
}
