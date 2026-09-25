package com.itilms.course.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.itilms.common.code.CodeLanguage;
import com.itilms.course.entity.Lesson;
import com.itilms.course.entity.LessonType;

class LessonResponseTest {

    private static Lesson lessonWithEditor() {
        return Lesson.builder().id(1L).moduleId(2L).title("Loops").type(LessonType.VIDEO)
                .contentUrl("https://example.com/loops").sequenceNo(1)
                .codeLanguage(CodeLanguage.JAVA).starterCode("class Main {}").build();
    }

    @Test
    void anUnlockedLessonCarriesItsPracticeEditor() {
        var response = LessonResponse.unlocked(lessonWithEditor(), null, null);

        assertThat(response.codeLanguage()).isEqualTo("JAVA");
        assertThat(response.starterCode()).isEqualTo("class Main {}");
    }

    @Test
    void aLockedLessonKeepsStarterCodeBackLikeAnyOtherMaterial() {
        var response = LessonResponse.locked(lessonWithEditor());

        assertThat(response.codeLanguage()).isNull();
        assertThat(response.starterCode()).isNull();
        assertThat(response.contentUrl()).isNull();
    }
}
