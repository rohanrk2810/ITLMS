package com.itilms.liveclass.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;
import com.itilms.liveclass.dto.request.AnswerQuestionRequest;
import com.itilms.liveclass.dto.request.AskQuestionRequest;
import com.itilms.liveclass.dto.response.LiveAnswerResponse;
import com.itilms.liveclass.dto.response.LiveQuestionResponse;
import com.itilms.liveclass.dto.response.LiveQuestionResponse.MyAnswer;
import com.itilms.liveclass.entity.LiveQuestion;
import com.itilms.liveclass.entity.LiveQuestionAnswer;
import com.itilms.liveclass.entity.LiveSession;
import com.itilms.liveclass.entity.QuestionType;
import com.itilms.liveclass.livekit.LiveKitGateway;
import com.itilms.liveclass.repository.LiveQuestionAnswerRepository;
import com.itilms.liveclass.repository.LiveQuestionRepository;
import com.itilms.liveclass.repository.LiveSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Questions asked during a live class.
 *
 * <p>Each question is stamped with how far into the class it was asked, so a recording can offer it at the same
 * moment. Only the class trainer and staff ask, close and read answers; a student sees a question of their own batch,
 * answers it once, and never sees the answer key until the question has closed or they have answered.
 */
@Service
@RequiredArgsConstructor
public class LiveQuestionService {

    private static final String SERVICE_NAME = "liveclass-service";
    private static final String TOPIC_ASKED = "live-question";
    private static final String TOPIC_CLOSED = "live-question-closed";
    private static final TypeReference<List<Integer>> INTS = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final LiveQuestionRepository questions;
    private final LiveQuestionAnswerRepository answers;
    private final LiveSessionRepository sessions;
    private final HostAccess hostAccess;
    private final LiveKitGateway liveKit;
    private final EventPublisher events;
    private final ObjectMapper json;

    // -----------------------------------------------------------------
    // The trainer
    // -----------------------------------------------------------------

    /** Asks a question now. Any question still open is closed first: the class answers one thing at a time. */
    public LiveQuestionResponse ask(Long liveSessionId, AskQuestionRequest request) {
        LiveSession session = sessions.findById(liveSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live session", liveSessionId));
        AppPrincipal host = hostAccess.requireHostOf(session);
        if (session.getStatus().isSettled() || session.isAttendanceComputed()) {
            throw new BusinessRuleException("CLASS_ENDED", "This class has finished; questions can no longer be asked.");
        }
        LiveQuestion question = build(session, host, request);

        for (LiveQuestion open : questions.findByLiveSessionIdAndStatus(liveSessionId, LiveQuestion.OPEN)) {
            close(open, session);
        }
        question = questions.save(question);
        events.audit(SERVICE_NAME, "LIVE_QUESTION_ASKED", "LiveQuestion", question.getId(), null,
                Map.of("type", question.getType().name(), "offsetSeconds", question.getOffsetSeconds()));
        liveKit.sendDataQuietly(session.getRoomName(), TOPIC_ASKED,
                "{\"id\":" + question.getId() + ",\"type\":\"" + question.getType() + "\"}", List.of());
        return toResponse(question, true, null, 0, 0);
    }

    public LiveQuestionResponse closeQuestion(Long questionId) {
        LiveQuestion question = requireQuestion(questionId);
        LiveSession session = requireSession(question.getLiveSessionId());
        hostAccess.requireHostOf(session);
        if (question.isOpen()) {
            close(question, session);
        }
        return withCounts(question);
    }

    public List<LiveAnswerResponse> answersTo(Long questionId) {
        LiveQuestion question = requireQuestion(questionId);
        hostAccess.requireHostOf(requireSession(question.getLiveSessionId()));
        return answers.findByQuestionIdOrderBySubmittedAtAsc(questionId).stream().map(this::toAnswer).toList();
    }

    // -----------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------

    /** All the questions of a class, in the order they were asked - the marks on a recording's timeline. */
    public List<LiveQuestionResponse> forClass(Long classSessionId) {
        LiveSession session = requireByClassSession(classSessionId);
        boolean host = isHost(session);
        List<LiveQuestion> all = questions.findByLiveSessionIdOrderByOffsetSecondsAscIdAsc(session.getId());
        return present(all, host);
    }

    /** The question the class is answering right now, if any. Students poll this as well as listening for pushes. */
    public LiveQuestionResponse openQuestion(Long classSessionId) {
        LiveSession session = requireByClassSession(classSessionId);
        boolean host = isHost(session);
        return questions.findFirstByLiveSessionIdAndStatusOrderByIdDesc(session.getId(), LiveQuestion.OPEN)
                .map(q -> present(List.of(q), host).get(0))
                .orElse(null);
    }

    // -----------------------------------------------------------------
    // The student
    // -----------------------------------------------------------------

    /**
     * Records the caller's answer. One answer per student: a question answered live cannot be answered again from
     * the recording, so what the trainer sees is what the student first said.
     */
    public LiveQuestionResponse answer(Long questionId, AnswerQuestionRequest request) {
        AppPrincipal student = SecurityUtils.requirePrincipal();
        if (!student.isStudent()) {
            throw new ForbiddenOperationException("Only students answer questions.");
        }
        LiveQuestion question = requireQuestion(questionId);
        LiveSession session = requireSession(question.getLiveSessionId());
        hostAccess.requireEnrolled(student, session);
        if (answers.findByQuestionIdAndUserId(questionId, student.userId()).isPresent()) {
            throw new BusinessRuleException("ALREADY_ANSWERED", "You have already answered this question.");
        }

        List<String> options = read(question.getOptions(), STRINGS);
        validateAnswer(question, options, request);
        Boolean correct = QuestionGrader.grade(question.getType(), read(question.getCorrectOptions(), INTS),
                read(question.getAcceptedAnswers(), STRINGS), request.selected(), request.text());

        LiveQuestionAnswer saved = answers.save(LiveQuestionAnswer.builder()
                .questionId(questionId).userId(student.userId()).studentId(student.profileId())
                .displayName(student.fullName())
                .selected(question.getType().isChoice() ? write(request.selected()) : null)
                .answerText(blankToNull(request.text()))
                .code(question.getType() == QuestionType.CODING ? request.code() : null)
                .language(question.getType() == QuestionType.CODING ? languageOf(question, request) : null)
                .correct(correct)
                .awardedMarks(correct == null ? null : correct ? question.getMarks() : 0)
                .viaRecording(!question.isOpen())
                .submittedAt(Instant.now())
                .build());
        return toResponse(question, false, saved, null, null);
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private LiveQuestion build(LiveSession session, AppPrincipal host, AskQuestionRequest r) {
        QuestionType type = r.type();
        List<String> options = null;
        List<Integer> correct = null;
        List<String> accepted = null;

        if (type == QuestionType.TRUE_FALSE) {
            options = List.of("True", "False");
            correct = r.correctOptions();
            if (correct == null || correct.size() != 1 || correct.get(0) < 0 || correct.get(0) > 1) {
                throw new BusinessRuleException("Say whether the statement is true or false.");
            }
        } else if (type == QuestionType.MCQ || type == QuestionType.MULTIPLE_SELECT) {
            options = r.options() == null ? List.of() : r.options().stream().map(o -> o == null ? "" : o.trim()).toList();
            if (options.size() < 2 || options.size() > 8 || options.stream().anyMatch(String::isEmpty)) {
                throw new BusinessRuleException("Give between 2 and 8 options, none of them blank.");
            }
            correct = r.correctOptions() == null ? List.of() : r.correctOptions().stream().distinct().sorted().toList();
            if (correct.isEmpty() || correct.stream().anyMatch(i -> i == null || i < 0 || i >= r.options().size())) {
                throw new BusinessRuleException("Mark which options are right.");
            }
            if (type == QuestionType.MCQ && correct.size() != 1) {
                throw new BusinessRuleException("A single-answer question has exactly one right option; "
                        + "use Multiple select for more.");
            }
        } else if (type == QuestionType.SHORT_ANSWER && r.acceptedAnswers() != null) {
            accepted = r.acceptedAnswers().stream().filter(a -> a != null && !a.isBlank()).map(String::trim).toList();
        }
        int marks = r.marks() == null ? 1 : r.marks();
        if (marks < 1 || marks > 100) {
            throw new BusinessRuleException("Marks must be between 1 and 100.");
        }

        Instant now = Instant.now();
        Instant classStart = session.getStartedAt() != null ? session.getStartedAt() : session.getScheduledStartAt();
        return LiveQuestion.builder()
                .liveSessionId(session.getId()).classSessionId(session.getClassSessionId()).batchId(session.getBatchId())
                .askedByUserId(host.userId()).type(type).prompt(r.prompt().trim())
                .options(options == null ? null : write(options))
                .correctOptions(correct == null ? null : write(correct))
                .acceptedAnswers(accepted == null || accepted.isEmpty() ? null : write(accepted))
                .language(type == QuestionType.CODING ? (r.language() == null || r.language().isBlank() ? "python" : r.language().trim().toLowerCase()) : null)
                .starterCode(type == QuestionType.CODING ? blankToNull(r.starterCode()) : null)
                .explanation(blankToNull(r.explanation()))
                .marks(marks).askedAt(now)
                .offsetSeconds(QuestionGrader.offsetSeconds(classStart, now))
                .build();
    }

    private void validateAnswer(LiveQuestion question, List<String> options, AnswerQuestionRequest r) {
        QuestionType type = question.getType();
        if (type.isChoice()) {
            List<Integer> picked = r.selected();
            if (picked == null || picked.isEmpty()) {
                throw new BusinessRuleException("Pick an answer.");
            }
            if (picked.stream().anyMatch(i -> i == null || i < 0 || i >= options.size())) {
                throw new BusinessRuleException("That is not one of the options.");
            }
            if (type != QuestionType.MULTIPLE_SELECT && picked.size() != 1) {
                throw new BusinessRuleException("Pick one answer.");
            }
        } else if (type == QuestionType.CODING) {
            if (r.code() == null || r.code().isBlank()) {
                throw new BusinessRuleException("Write your code first.");
            }
        } else if (r.text() == null || r.text().isBlank()) {
            throw new BusinessRuleException("Write your answer.");
        }
    }

    private String languageOf(LiveQuestion question, AnswerQuestionRequest r) {
        return r.language() != null && !r.language().isBlank() ? r.language().trim().toLowerCase() : question.getLanguage();
    }

    private void close(LiveQuestion question, LiveSession session) {
        question.setStatus(LiveQuestion.CLOSED);
        question.setClosedAt(Instant.now());
        questions.save(question);
        liveKit.sendDataQuietly(session.getRoomName(), TOPIC_CLOSED, "{\"id\":" + question.getId() + "}", List.of());
    }

    private boolean isHost(LiveSession session) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (caller.isStaff() || caller.isTrainer()) {
            hostAccess.requireHostOf(session);
            return true;
        }
        if (caller.isStudent()) {
            hostAccess.requireEnrolled(caller, session);
            return false;
        }
        throw new ForbiddenOperationException("You cannot see this class's questions.");
    }

    private List<LiveQuestionResponse> present(List<LiveQuestion> list, boolean host) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(LiveQuestion::getId).toList();
        if (host) {
            Map<Long, List<LiveQuestionAnswer>> byQuestion = answers.findByQuestionIdIn(ids).stream()
                    .collect(Collectors.groupingBy(LiveQuestionAnswer::getQuestionId));
            return list.stream().map(q -> {
                List<LiveQuestionAnswer> given = byQuestion.getOrDefault(q.getId(), List.of());
                return toResponse(q, true, null, given.size(),
                        (int) given.stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count());
            }).toList();
        }
        Long me = SecurityUtils.requirePrincipal().userId();
        Map<Long, LiveQuestionAnswer> mine = answers.findByUserIdAndQuestionIdIn(me, ids).stream()
                .collect(Collectors.toMap(LiveQuestionAnswer::getQuestionId, Function.identity()));
        return list.stream().map(q -> toResponse(q, false, mine.get(q.getId()), null, null)).toList();
    }

    private LiveQuestionResponse withCounts(LiveQuestion q) {
        List<LiveQuestionAnswer> given = answers.findByQuestionIdOrderBySubmittedAtAsc(q.getId());
        return toResponse(q, true, null, given.size(),
                (int) given.stream().filter(a -> Boolean.TRUE.equals(a.getCorrect())).count());
    }

    private LiveQuestionResponse toResponse(LiveQuestion q, boolean host, LiveQuestionAnswer mine,
                                            Integer answered, Integer correctCount) {
        boolean reveal = host || !q.isOpen() || mine != null;
        MyAnswer my = mine == null ? null : new MyAnswer(read(mine.getSelected(), INTS), mine.getAnswerText(),
                mine.getCode(), mine.getLanguage(), mine.getCorrect(), mine.getAwardedMarks(), mine.isViaRecording(),
                mine.getSubmittedAt());
        return new LiveQuestionResponse(q.getId(), q.getLiveSessionId(), q.getClassSessionId(), q.getType(),
                q.getPrompt(), read(q.getOptions(), STRINGS), q.getLanguage(), q.getStarterCode(), q.getMarks(),
                q.getStatus(), q.getAskedAt(), q.getOffsetSeconds(), QuestionGrader.label(q.getOffsetSeconds()),
                reveal ? read(q.getCorrectOptions(), INTS) : null,
                reveal ? read(q.getAcceptedAnswers(), STRINGS) : null,
                reveal ? q.getExplanation() : null,
                host ? answered : null, host ? correctCount : null, my);
    }

    private LiveAnswerResponse toAnswer(LiveQuestionAnswer a) {
        return new LiveAnswerResponse(a.getUserId(), a.getStudentId(), a.getDisplayName(), read(a.getSelected(), INTS),
                a.getAnswerText(), a.getCode(), a.getLanguage(), a.getCorrect(), a.getAwardedMarks(),
                a.isViaRecording(), a.getSubmittedAt());
    }

    private LiveQuestion requireQuestion(Long id) {
        return questions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Question", id));
    }

    private LiveSession requireSession(Long id) {
        return sessions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Live session", id));
    }

    private LiveSession requireByClassSession(Long classSessionId) {
        return sessions.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Live class for session", classSessionId));
    }

    private <T> List<T> read(String value, TypeReference<List<T>> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored question data is not valid JSON", ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
