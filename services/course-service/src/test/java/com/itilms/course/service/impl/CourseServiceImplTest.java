package com.itilms.course.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.common.event.CoursePublishedEvent;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.DuplicateResourceException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.course.dto.request.CreateCourseRequest;
import com.itilms.course.dto.request.UpdateCourseRequest;
import com.itilms.course.dto.response.CourseDetailResponse;
import com.itilms.course.dto.response.CourseResponse;
import com.itilms.course.dto.response.LessonResponse;
import com.itilms.course.entity.Course;
import com.itilms.course.entity.CourseEnrollment;
import com.itilms.course.entity.CourseModule;
import com.itilms.course.entity.CourseStatus;
import com.itilms.course.entity.EnrollmentStatus;
import com.itilms.course.entity.Lesson;
import com.itilms.course.entity.LessonType;
import com.itilms.course.repository.CourseEnrollmentRepository;
import com.itilms.course.repository.CourseModuleRepository;
import com.itilms.course.repository.CourseRepository;
import com.itilms.course.repository.LessonProgressRepository;
import com.itilms.course.repository.LessonRepository;

/** Course lifecycle (draft, published, archived) and who may see which lesson. */
@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    private static final long COURSE = 3L;

    @Mock CourseRepository courseRepository;
    @Mock CourseModuleRepository moduleRepository;
    @Mock LessonRepository lessonRepository;
    @Mock CourseEnrollmentRepository enrollmentRepository;
    @Mock LessonProgressRepository progressRepository;
    @Mock EventPublisher events;

    CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseServiceImpl(courseRepository, moduleRepository, lessonRepository,
                enrollmentRepository, progressRepository, events);
        lenient().when(courseRepository.save(any(Course.class))).thenAnswer(i -> {
            Course c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(COURSE);
            }
            return c;
        });
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

    private static Course course(CourseStatus status) {
        return Course.builder().id(COURSE).title("Java Full Stack").code("JFS-1").summary("Spring and React")
                .description("A complete programme").fee(new BigDecimal("45000")).status(status).build();
    }

    private static CreateCourseRequest create(String code, String level) {
        return new CreateCourseRequest(" Java ", code, null, null, null, null, null, null, level, null, null);
    }

    private static UpdateCourseRequest update(String code) {
        return new UpdateCourseRequest("Java", code, null, null, null, null, null, 100, "BEGINNER", null, null);
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Creating and editing")
    class Editing {

        @Test
        void aNewCourseIsADraftWithAnUpperCasedCodeAndDefaults() {
            CourseResponse response = service.create(create(" jfs-01 ", null));

            assertThat(response.code()).isEqualTo("JFS-01");
            assertThat(response.status()).isEqualTo("DRAFT");
            assertThat(response.level()).isEqualTo("BEGINNER");
            assertThat(response.fee()).isEqualByComparingTo("0");
            verify(events).audit(eq("course-service"), eq("COURSE_CREATED"), eq("Course"), eq(COURSE), any(), any());
        }

        @Test
        void aDuplicateCodeIsRefusedIgnoringCase() {
            when(courseRepository.existsByCodeIgnoreCase("JFS-01")).thenReturn(true);

            assertThatThrownBy(() -> service.create(create("jfs-01", null))).isInstanceOf(DuplicateResourceException.class);
            verify(courseRepository, never()).save(any());
        }

        @Test
        void anUnknownLevelIsRefused() {
            assertThatThrownBy(() -> service.create(create("X-1", "EXPERT")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("BEGINNER, INTERMEDIATE or ADVANCED");
        }

        @Test
        void anArchivedCourseCannotBeEdited() {
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(CourseStatus.ARCHIVED)));

            assertThatThrownBy(() -> service.update(COURSE, update("JFS-1")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("archived");
        }

        @Test
        void anEditMayNotTakeAnotherCoursesCodeButMayKeepItsOwn() {
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
            when(courseRepository.existsByCodeExcluding("TAKEN", COURSE)).thenReturn(true);
            when(courseRepository.existsByCodeExcluding("JFS-1", COURSE)).thenReturn(false);

            assertThatThrownBy(() -> service.update(COURSE, update("taken"))).isInstanceOf(DuplicateResourceException.class);
            assertThat(service.update(COURSE, update("jfs-1")).code()).isEqualTo("JFS-1");
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Publishing")
    class Publishing {

        @Test
        void aBareCourseNamesEverythingItStillNeeds() {
            Course bare = Course.builder().id(COURSE).title("Java").code("J-1").status(CourseStatus.DRAFT).build();
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(bare));
            when(moduleRepository.countByCourseId(COURSE)).thenReturn(0L);
            when(courseRepository.findLessonIdsByCourse(COURSE)).thenReturn(List.of());

            assertThatThrownBy(() -> service.publish(COURSE)).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("summary").hasMessageContaining("description")
                    .hasMessageContaining("module").hasMessageContaining("lesson");
            assertThat(bare.getStatus()).isEqualTo(CourseStatus.DRAFT);
            verify(events, never()).publishAfterCommit(anyString(), any());
        }

        @Test
        void aCourseWhoseLessonsAreAllOptionalCannotBePublishedBecauseNobodyCouldFinishIt() {
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
            when(moduleRepository.countByCourseId(COURSE)).thenReturn(1L);
            when(courseRepository.findLessonIdsByCourse(COURSE)).thenReturn(List.of(10L, 11L));
            when(courseRepository.countMandatoryLessons(COURSE)).thenReturn(0);

            assertThatThrownBy(() -> service.publish(COURSE))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("mandatory lesson");
        }

        @Test
        void aCompleteCourseIsPublishedAndAnnounced() {
            Course draft = course(CourseStatus.DRAFT);
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(draft));
            when(moduleRepository.countByCourseId(COURSE)).thenReturn(2L);
            when(courseRepository.findLessonIdsByCourse(COURSE)).thenReturn(List.of(10L, 11L, 12L));
            when(courseRepository.countMandatoryLessons(COURSE)).thenReturn(2);

            CourseResponse response = service.publish(COURSE);

            assertThat(response.status()).isEqualTo("PUBLISHED");
            assertThat(draft.getPublishedAt()).isNotNull();
            verify(events).publishAfterCommit(eq(KafkaTopics.COURSE_PUBLISHED), any(CoursePublishedEvent.class));
            verify(events).audit(eq("course-service"), eq("COURSE_PUBLISHED"), eq("Course"), eq(COURSE), any(), any());
        }

        @Test
        void publishingAPublishedCourseAgainIsAQuietNoOp() {
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(CourseStatus.PUBLISHED)));

            assertThat(service.publish(COURSE).status()).isEqualTo("PUBLISHED");

            verify(events, never()).publishAfterCommit(anyString(), any());
            verify(courseRepository, never()).save(any());
        }

        @Test
        void archivingTakesACourseOffTheCatalogAndIsAudited() {
            Course live = course(CourseStatus.PUBLISHED);
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(live));

            service.archive(COURSE);

            assertThat(live.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
            verify(events).audit(eq("course-service"), eq("COURSE_ARCHIVED"), eq("Course"), eq(COURSE), any(), any());
        }

        @Test
        void anUnknownCourseIsNotFound() {
            when(courseRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publish(404L)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Who can open the curriculum")
    class Visibility {

        private final CourseModule module = CourseModule.builder().id(20L).courseId(COURSE).title("Core Java").sequenceNo(1).build();
        private final Lesson previewLesson = Lesson.builder().id(30L).moduleId(20L).title("Welcome").type(LessonType.VIDEO)
                .contentUrl("https://example.com/welcome").sequenceNo(1).preview(true).build();
        private final Lesson paidLesson = Lesson.builder().id(31L).moduleId(20L).title("Streams").type(LessonType.VIDEO)
                .contentUrl("https://example.com/streams").sequenceNo(2).build();

        private void curriculumExists(Course course) {
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course));
            lenient().when(moduleRepository.findByCourseIdOrderBySequenceNo(COURSE)).thenReturn(List.of(module));
            lenient().when(lessonRepository.findByModuleIdInOrderByModuleIdAscSequenceNoAsc(any())).thenReturn(List.of(previewLesson, paidLesson));
        }

        private CourseEnrollment enrolled() {
            return CourseEnrollment.builder().id(60L).enrollmentId(5L).studentId(10L).userId(1L).courseId(COURSE).build();
        }

        private LessonResponse lesson(CourseDetailResponse detail, long id) {
            return detail.modules().get(0).lessons().stream().filter(l -> l.id() == id).findFirst().orElseThrow();
        }

        @Test
        void aVisitorSeesAPublishedCourseWithOnlyThePreviewLessonUnlocked() {
            curriculumExists(course(CourseStatus.PUBLISHED));

            CourseDetailResponse detail = service.getDetail(COURSE);

            assertThat(detail.enrolled()).isFalse();
            assertThat(lesson(detail, 30L).accessible()).isTrue();
            LessonResponse locked = lesson(detail, 31L);
            assertThat(locked.accessible()).isFalse();
            assertThat(locked.contentUrl()).as("locked content is not sent to the client at all").isNull();
        }

        @Test
        void aDraftDoesNotExistForVisitorsOrStudents() {
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));

            assertThatThrownBy(() -> service.getDetail(COURSE)).isInstanceOf(ResourceNotFoundException.class);

            actAs("STUDENT", 10L);
            assertThatThrownBy(() -> service.getDetail(COURSE)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void staffSeeADraftWithEverythingUnlocked() {
            curriculumExists(course(CourseStatus.DRAFT));
            actAs("TRAINER", 20L);

            CourseDetailResponse detail = service.getDetail(COURSE);

            assertThat(lesson(detail, 31L).accessible()).isTrue();
            assertThat(lesson(detail, 31L).contentUrl()).isNotNull();
        }

        @Test
        void anEnrolledStudentSeesEverythingWithTheirProgress() {
            curriculumExists(course(CourseStatus.PUBLISHED));
            actAs("STUDENT", 10L);
            when(enrollmentRepository.findByStudentIdAndCourseId(10L, COURSE)).thenReturn(Optional.of(enrolled()));
            when(progressRepository.findByEnrollmentId(60L)).thenReturn(List.of());

            CourseDetailResponse detail = service.getDetail(COURSE);

            assertThat(detail.enrolled()).isTrue();
            assertThat(lesson(detail, 31L).accessible()).isTrue();
            assertThat(lesson(detail, 31L).completed()).isFalse();
            assertThat(detail.progress()).isNotNull();
        }

        @Test
        void aStudentWhoIsNotEnrolledGetsTheLockedView() {
            curriculumExists(course(CourseStatus.PUBLISHED));
            actAs("STUDENT", 10L);
            when(enrollmentRepository.findByStudentIdAndCourseId(10L, COURSE)).thenReturn(Optional.empty());

            CourseDetailResponse detail = service.getDetail(COURSE);

            assertThat(lesson(detail, 31L).accessible()).isFalse();
            assertThat(detail.progress()).isNull();
        }

        /** Content is reachable only through an enrolment that still counts (Doc S14); progress already stops on DROPPED. */
        @Test
        void aDroppedOrSuspendedEnrolmentNoLongerUnlocksTheLessons() {
            curriculumExists(course(CourseStatus.PUBLISHED));
            actAs("STUDENT", 10L);

            for (EnrollmentStatus status : List.of(EnrollmentStatus.DROPPED, EnrollmentStatus.SUSPENDED)) {
                CourseEnrollment closed = enrolled();
                closed.setStatus(status);
                when(enrollmentRepository.findByStudentIdAndCourseId(10L, COURSE)).thenReturn(Optional.of(closed));

                CourseDetailResponse detail = service.getDetail(COURSE);

                assertThat(detail.enrolled()).as(status + " is not an active enrolment").isFalse();
                assertThat(lesson(detail, 31L).accessible()).isFalse();
                assertThat(detail.progress()).isNull();
            }
        }

        @Test
        void aCompletedEnrolmentKeepsTheLessonsOpenForRevision() {
            curriculumExists(course(CourseStatus.PUBLISHED));
            actAs("STUDENT", 10L);
            CourseEnrollment done = enrolled();
            done.setStatus(EnrollmentStatus.COMPLETED);
            when(enrollmentRepository.findByStudentIdAndCourseId(10L, COURSE)).thenReturn(Optional.of(done));
            when(progressRepository.findByEnrollmentId(60L)).thenReturn(List.of());

            assertThat(lesson(service.getDetail(COURSE), 31L).accessible()).isTrue();
        }

        /** Archiving "leaves the catalog"; it must not lock out the people who already took the course. */
        @Test
        void anEnrolledStudentKeepsAccessToACourseThatIsLaterArchived() {
            curriculumExists(course(CourseStatus.ARCHIVED));
            actAs("STUDENT", 10L);
            when(enrollmentRepository.findByStudentIdAndCourseId(10L, COURSE)).thenReturn(Optional.of(enrolled()));
            when(progressRepository.findByEnrollmentId(60L)).thenReturn(List.of());

            CourseDetailResponse detail = service.getDetail(COURSE);

            assertThat(detail.enrolled()).isTrue();
            assertThat(lesson(detail, 31L).accessible()).isTrue();
        }

        @Test
        void anArchivedCourseIsGoneForEveryoneWhoNeverTookIt() {
            when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course(CourseStatus.ARCHIVED)));
            actAs("STUDENT", 10L);
            when(enrollmentRepository.findByStudentIdAndCourseId(anyLong(), anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDetail(COURSE)).isInstanceOf(ResourceNotFoundException.class);

            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.getDetail(COURSE)).isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
