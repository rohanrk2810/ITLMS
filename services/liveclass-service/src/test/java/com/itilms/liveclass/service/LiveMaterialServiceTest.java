package com.itilms.liveclass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.liveclass.dto.request.AskQuestionRequest;
import com.itilms.liveclass.dto.request.MaterialRequest;
import com.itilms.liveclass.dto.response.MaterialResponse;
import com.itilms.liveclass.entity.LiveMaterial;
import com.itilms.liveclass.entity.QuestionType;
import com.itilms.liveclass.repository.LiveMaterialRepository;

class LiveMaterialServiceTest {

    private LiveMaterialRepository repository;
    private LiveMaterialService service;

    @BeforeEach
    void setUp() {
        repository = mock(LiveMaterialRepository.class);
        service = new LiveMaterialService(repository, mock(LiveQuestionService.class), new ObjectMapper());
        when(repository.save(any(LiveMaterial.class))).thenAnswer(i -> i.getArgument(0));
        signInAs(7L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(Long userId) {
        AppPrincipal trainer = new AppPrincipal(userId, "t@x", "Trainer " + userId, "TRAINER", userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(trainer, null, trainer.authorities()));
    }

    private static MaterialRequest request(String title, String topic, List<String> tags, boolean shared) {
        AskQuestionRequest q = new AskQuestionRequest(QuestionType.SHORT_ANSWER, "What does it print?", null, null,
                List.of("6"), null, null, null, 1, "print(3*2)", "python", null);
        return new MaterialRequest(title, q, null, topic, tags, "EASY", shared);
    }

    @Test
    void tagsAreLowerCasedAndDeduplicated() {
        MaterialResponse saved = service.create(request("Loops", "Python", List.of("Loops", " loops ", "FOR"), false));

        assertThat(saved.tags()).containsExactly("loops", "for");
        assertThat(saved.mine()).isTrue();
        assertThat(saved.ownerName()).isEqualTo("Trainer 7");
    }

    @Test
    void searchFindsByTagTopicAndQuestionText() {
        LiveMaterial mine = stored(1L, 7L, "Joins", "SQL", "joins,sql", false);
        LiveMaterial other = stored(2L, 8L, "Loops", "Python", "loops", true);
        when(repository.findByOwnerUserIdOrSharedTrueOrderByCreatedAtDesc(7L)).thenReturn(List.of(mine, other));

        assertThat(service.list(null, null, null, "SQL", null)).extracting(MaterialResponse::id).containsExactly(1L);
        assertThat(service.list(null, "python", null, null, null)).extracting(MaterialResponse::id).containsExactly(2L);
        assertThat(service.list("shared", null, null, null, null)).extracting(MaterialResponse::id).containsExactly(2L);
        assertThat(service.list("mine", null, null, null, null)).extracting(MaterialResponse::id).containsExactly(1L);
        assertThat(service.list(null, "print(3*2)", null, null, null)).hasSize(2);
    }

    @Test
    void anotherTrainerCannotChangeOrSeeAPrivateItem() {
        LiveMaterial theirs = stored(5L, 8L, "Private", null, null, false);
        when(repository.findById(5L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.update(5L, request("x", null, null, false)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.copy(5L)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void copyingASharedItemMakesAPrivateCopyOwnedByTheCaller() {
        LiveMaterial shared = stored(6L, 8L, "Shared", "SQL", "sql", true);
        when(repository.findById(6L)).thenReturn(Optional.of(shared));

        MaterialResponse copy = service.copy(6L);

        assertThat(copy.mine()).isTrue();
        assertThat(copy.shared()).isFalse();
        assertThat(copy.topic()).isEqualTo("SQL");
    }

    private LiveMaterial stored(Long id, Long owner, String title, String topic, String tags, boolean shared) {
        return LiveMaterial.builder().id(id).ownerUserId(owner).ownerName("Trainer " + owner).title(title)
                .topic(topic).tags(tags).shared(shared)
                .payload("{\"type\":\"SHORT_ANSWER\",\"prompt\":\"What does it print?\",\"snippet\":\"print(3*2)\"}")
                .build();
    }
}
