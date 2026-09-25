package com.itilms.course.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.event.KafkaTopics;
import com.itilms.common.event.LessonPublishedEvent;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.course.dto.request.LessonRequest;
import com.itilms.course.dto.request.ModuleRequest;
import com.itilms.course.entity.Course;
import com.itilms.course.entity.CourseModule;
import com.itilms.course.entity.CourseStatus;
import com.itilms.course.entity.Lesson;
import com.itilms.course.entity.LessonType;
import com.itilms.course.repository.CourseModuleRepository;
import com.itilms.course.repository.CourseRepository;
import com.itilms.course.repository.LessonProgressRepository;
import com.itilms.course.repository.LessonRepository;
import com.itilms.course.service.ProgressService;

/**
 * Editing a curriculum changes what every enrolled student has to finish, so these tests pin
 * when progress is recalculated and which edits are refused.
 */
@ExtendWith(MockitoExtension.class)
class CurriculumServiceImplTest {

    private static final long COURSE = 3L;
    private static final long MODULE = 20L;

    @Mock CourseRepository courseRepository;
    @Mock CourseModuleRepository moduleRepository;
    @Mock LessonRepository lessonRepository;
    @Mock LessonProgressRepository progressRepository;
    @Mock ProgressService progressService;
    @Mock EventPublisher events;

    CurriculumServiceImpl service;
    Course course;

    @BeforeEach
    void setUp() {
        service = new CurriculumServiceImpl(courseRepository, moduleRepository, lessonRepository,
                progressRepository, progressService, events);
        course = Course.builder().id(COURSE).title("Java").code("J-1").status(CourseStatus.DRAFT).build();
        lenient().when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course));
        lenient().when(moduleRepository.findById(MODULE)).thenReturn(Optional.of(module(MODULE, 1)));
        lenient().when(moduleRepository.save(any(CourseModule.class))).thenAnswer(i -> {
            CourseModule m = i.getArgument(0);
            if (m.getId() == null) {
                m.setId(MODULE);
            }
            return m;
        });
        lenient().when(lessonRepository.save(any(Lesson.class))).thenAnswer(i -> {
            Lesson l = i.getArgument(0);
            if (l.getId() == null) {
                l.setId(30L);
            }
            return l;
        });
    }

    private static CourseModule module(long id, int sequence) {
        return CourseModule.builder().id(id).courseId(COURSE).title("Module " + id).sequenceNo(sequence).build();
    }

    private static Lesson lesson(long id, int sequence, boolean mandatory) {
        return Lesson.builder().id(id).moduleId(MODULE).title("Lesson " + id).type(LessonType.VIDEO)
                .contentUrl("https://example.com/" + id).sequenceNo(sequence).mandatory(mandatory).build();
    }

    private static LessonRequest video(Boolean mandatory) {
        return new LessonRequest(" JVM basics ", "video", "https://example.com/jvm", null, null, 30, null, null, mandatory, null, null);
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Modules")
    class Modules {

        @Test
        void aNewModuleIsAppendedWhenNoPositionIsGiven() {
            when(moduleRepository.nextSequenceNo(COURSE)).thenReturn(4);

            var response = service.addModule(COURSE, new ModuleRequest(" Spring ", null, null));

            assertThat(response.sequenceNo()).isEqualTo(4);
            assertThat(response.title()).isEqualTo("Spring");
        }

        @Test
        void aPositionAlreadyInUseIsRefusedWithAHelpfulMessage() {
            when(moduleRepository.findByCourseIdAndSequenceNo(COURSE, 2)).thenReturn(Optional.of(module(21, 2)));

            assertThatThrownBy(() -> service.addModule(COURSE, new ModuleRequest("Spring", null, 2)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Position 2");
            verify(moduleRepository, never()).save(any());
        }

        @Test
        void anArchivedCourseTakesNoCurriculumEdits() {
            course.setStatus(CourseStatus.ARCHIVED);

            assertThatThrownBy(() -> service.addModule(COURSE, new ModuleRequest("Spring", null, null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("archived");
            assertThatThrownBy(() -> service.addLesson(MODULE, video(null)))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.reorderModules(COURSE, List.of(1L)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void aModuleWithALessonStudentsHaveFinishedCannotBeDeleted() {
            when(lessonRepository.findByModuleIdOrderBySequenceNo(MODULE)).thenReturn(List.of(lesson(30, 1, true), lesson(31, 2, true)));
            when(progressRepository.countByLessonIdAndCompletedTrue(30L)).thenReturn(0L);
            when(progressRepository.countByLessonIdAndCompletedTrue(31L)).thenReturn(4L);

            assertThatThrownBy(() -> service.deleteModule(MODULE))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("4 student(s)").hasMessageContaining("optional");

            verify(moduleRepository, never()).delete(any());
            verify(progressService, never()).recalculateCourse(anyLong());
        }

        @Test
        void anUntouchedModuleIsDeletedAndEveryonesProgressIsRecalculated() {
            when(lessonRepository.findByModuleIdOrderBySequenceNo(MODULE)).thenReturn(List.of(lesson(30, 1, true)));
            when(progressRepository.countByLessonIdAndCompletedTrue(30L)).thenReturn(0L);

            service.deleteModule(MODULE);

            verify(moduleRepository).delete(any(CourseModule.class));
            verify(progressService).recalculateCourse(COURSE);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Adding and editing lessons")
    class Lessons {

        @Test
        void aMandatoryLessonLowersEveryonesPercentageSoProgressIsRecalculated() {
            when(lessonRepository.nextSequenceNo(MODULE)).thenReturn(1);

            var response = service.addLesson(MODULE, video(null));

            assertThat(response.mandatory()).as("lessons are mandatory unless said otherwise").isTrue();
            assertThat(response.title()).isEqualTo("JVM basics");
            verify(progressService).recalculateCourse(COURSE);
        }

        @Test
        void anOptionalLessonDoesNotTouchAnyonesProgress() {
            when(lessonRepository.nextSequenceNo(MODULE)).thenReturn(1);

            service.addLesson(MODULE, video(false));

            verify(progressService, never()).recalculateCourse(anyLong());
        }

        @Test
        void studentsAreToldAboutNewMaterialOnlyOnceTheCourseIsLive() {
            when(lessonRepository.nextSequenceNo(MODULE)).thenReturn(1);

            service.addLesson(MODULE, video(true));
            verify(events, never()).publishAfterCommit(anyString(), any());

            course.setStatus(CourseStatus.PUBLISHED);
            service.addLesson(MODULE, video(true));
            verify(events).publishAfterCommit(org.mockito.ArgumentMatchers.eq(KafkaTopics.LESSON_PUBLISHED), any(LessonPublishedEvent.class));
        }

        @Test
        void eachLessonTypeNeedsItsOwnKindOfMaterial() {
            LessonRequest videoNoUrl = new LessonRequest("V", "VIDEO", null, null, "some text", null, null, null, null, null, null);
            LessonRequest linkNoUrl = new LessonRequest("L", "LINK", null, "file-1", null, null, null, null, null, null, null);
            LessonRequest noteNoText = new LessonRequest("N", "NOTE", "https://x.test", null, null, null, null, null, null, null, null);
            LessonRequest textNoText = new LessonRequest("T", "TEXT", null, "file-1", null, null, null, null, null, null, null);
            LessonRequest pdfNothing = new LessonRequest("P", "PDF", null, null, "text only", null, null, null, null, null, null);
            LessonRequest nothing = new LessonRequest("X", "VIDEO", " ", " ", " ", null, null, null, null, null, null);

            for (LessonRequest bad : List.of(videoNoUrl, linkNoUrl, noteNoText, textNoText, pdfNothing, nothing)) {
                assertThatThrownBy(() -> service.addLesson(MODULE, bad)).as(bad.title()).isInstanceOf(BusinessRuleException.class);
            }
            verify(lessonRepository, never()).save(any());
        }

        @Test
        void aPdfAcceptsAnUploadedFileOrALink() {
            when(lessonRepository.nextSequenceNo(MODULE)).thenReturn(1);

            service.addLesson(MODULE, new LessonRequest("Notes", "PDF", null, "file-9", null, null, null, null, false, null, null));
            service.addLesson(MODULE, new LessonRequest("Notes 2", "pdf", "https://x.test/n.pdf", null, null, null, null, null, false, null, null));

            verify(lessonRepository, org.mockito.Mockito.times(2)).save(any(Lesson.class));
        }

        @Test
        void anUnknownLessonTypeIsRefused() {
            assertThatThrownBy(() -> service.addLesson(MODULE, new LessonRequest("S", "HOLOGRAM", "https://x.test", null, null, null, null, null, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("VIDEO, PDF, NOTE, LINK or TEXT");
        }

        @Test
        void aLessonOfAnyTypeCanCarryAPracticeEditorWithItsIndentationIntact() {
            when(lessonRepository.nextSequenceNo(MODULE)).thenReturn(1);
            String starter = "public class Main {\n    public static void main(String[] a) {\n    }\n}\n";

            var response = service.addLesson(MODULE, new LessonRequest(
                    "JVM basics", "VIDEO", "https://example.com/jvm", null, null, 30, null, null, null, " java ", starter));

            assertThat(response.codeLanguage()).isEqualTo("JAVA");
            assertThat(response.starterCode()).isEqualTo(starter);
        }

        @Test
        void aLessonWithoutAPracticeLanguageHasNoEditorAndDropsAnyStarterCode() {
            when(lessonRepository.nextSequenceNo(MODULE)).thenReturn(1);

            var response = service.addLesson(MODULE, video(null));

            assertThat(response.codeLanguage()).isNull();
            assertThat(response.starterCode()).isNull();
        }

        @Test
        void starterCodeWithoutALanguageIsRefused() {
            assertThatThrownBy(() -> service.addLesson(MODULE, new LessonRequest(
                    "V", "VIDEO", "https://x.test", null, null, null, null, null, null, " ", "print(1)")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("practice language");
            verify(lessonRepository, never()).save(any());
        }

        @Test
        void anUnsupportedPracticeLanguageIsRefusedAndTheAllowedOnesAreListed() {
            // PL/SQL is deliberately unsupported: it needs an Oracle database, not a compiler.
            assertThatThrownBy(() -> service.addLesson(MODULE, new LessonRequest(
                    "V", "VIDEO", "https://x.test", null, null, null, null, null, null, "PLSQL", null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("JAVA, PYTHON, C, CPP, CSHARP or SQL");
        }

        @Test
        void editingALessonCanAddChangeAndRemoveItsPracticeEditor() {
            Lesson existing = lesson(30, 1, true);
            when(lessonRepository.findById(30L)).thenReturn(Optional.of(existing));

            service.updateLesson(30L, new LessonRequest(
                    "V", "VIDEO", "https://x.test", null, null, null, null, null, true, "python", "print('hi')"));
            assertThat(existing.getCodeLanguage()).isEqualTo(com.itilms.common.code.CodeLanguage.PYTHON);
            assertThat(existing.getStarterCode()).isEqualTo("print('hi')");

            service.updateLesson(30L, video(true));
            assertThat(existing.getCodeLanguage()).as("omitting the language removes the editor").isNull();
            assertThat(existing.getStarterCode()).isNull();
        }

        @Test
        void makingALessonOptionalRecalculatesButAnEditThatKeepsItMandatoryDoesNot() {
            Lesson existing = lesson(30, 1, true);
            when(lessonRepository.findById(30L)).thenReturn(Optional.of(existing));

            service.updateLesson(30L, video(true));
            verify(progressService, never()).recalculateCourse(anyLong());

            service.updateLesson(30L, video(false));
            assertThat(existing.isMandatory()).isFalse();
            verify(progressService).recalculateCourse(COURSE);
        }

        @Test
        void aLessonSomeoneHasFinishedCannotBeDeleted() {
            when(lessonRepository.findById(30L)).thenReturn(Optional.of(lesson(30, 1, true)));
            when(progressRepository.countByLessonIdAndCompletedTrue(30L)).thenReturn(1L);

            assertThatThrownBy(() -> service.deleteLesson(30L)).isInstanceOf(BusinessRuleException.class);

            verify(lessonRepository, never()).delete(any());
        }

        @Test
        void anUnfinishedLessonIsDeletedAndProgressRecalculated() {
            Lesson existing = lesson(30, 1, true);
            when(lessonRepository.findById(30L)).thenReturn(Optional.of(existing));
            when(progressRepository.countByLessonIdAndCompletedTrue(30L)).thenReturn(0L);

            service.deleteLesson(30L);

            verify(lessonRepository).delete(existing);
            verify(progressService).recalculateCourse(COURSE);
        }
    }

    // ------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Reordering")
    class Reordering {

        @Test
        void aReorderMustListEveryLessonExactlyOnce() {
            when(lessonRepository.findByModuleIdOrderBySequenceNo(MODULE)).thenReturn(List.of(lesson(1, 1, true), lesson(2, 2, true), lesson(3, 3, true)));

            assertThatThrownBy(() -> service.reorderLessons(MODULE, List.of(1L, 2L))).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.reorderLessons(MODULE, List.of(1L, 2L, 2L))).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.reorderLessons(MODULE, List.of(1L, 2L, 3L, 4L))).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.reorderLessons(MODULE, List.of(1L, 2L, 9L))).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.reorderLessons(MODULE, null)).isInstanceOf(BusinessRuleException.class);
            verify(lessonRepository, never()).saveAllAndFlush(any());
        }

        @Test
        void positionsAreParkedNegativeFirstSoTheUniqueConstraintNeverSeesADuplicate() {
            Lesson a = lesson(1, 1, true);
            Lesson b = lesson(2, 2, true);
            Lesson c = lesson(3, 3, true);
            when(lessonRepository.findByModuleIdOrderBySequenceNo(MODULE)).thenReturn(List.of(a, b, c));
            List<List<Integer>> flushed = new ArrayList<>();
            when(lessonRepository.saveAllAndFlush(any())).thenAnswer(i -> {
                Iterable<Lesson> lessons = i.getArgument(0);
                List<Integer> snapshot = new ArrayList<>();
                lessons.forEach(l -> snapshot.add(l.getSequenceNo()));
                flushed.add(snapshot);
                return List.of();
            });

            service.reorderLessons(MODULE, List.of(3L, 1L, 2L));

            assertThat(flushed).hasSize(2);
            assertThat(flushed.get(0)).as("first flush: everything parked below zero").allMatch(n -> n < 0);
            assertThat(c.getSequenceNo()).isEqualTo(1);
            assertThat(a.getSequenceNo()).isEqualTo(2);
            assertThat(b.getSequenceNo()).isEqualTo(3);
        }
    }
}
