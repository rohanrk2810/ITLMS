package com.itilms.assessment.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.itilms.assessment.dto.request.CreateQuizRequest;
import com.itilms.assessment.dto.request.QuestionRequest;
import com.itilms.assessment.dto.response.QuizResponse;
import com.itilms.assessment.dto.response.StudentQuizResponse;
import com.itilms.common.dto.PageResponse;

/** Writing and running MCQ tests (Doc S6.11). */
public interface QuizService {

    QuizResponse create(CreateQuizRequest request);

    /** Settings can change only while the test is a draft; see the implementation for why. */
    QuizResponse update(Long id, CreateQuizRequest request);

    QuizResponse addQuestion(Long quizId, QuestionRequest request);

    QuizResponse updateQuestion(Long questionId, QuestionRequest request);

    QuizResponse deleteQuestion(Long questionId);

    QuizResponse publish(Long id);

    /**
     * Closes the test: no new attempts, running ones are scored as they stand,
     * and results that were held back become visible to students.
     */
    QuizResponse close(Long id);

    /** The full test with its answer key - trainers of the batch or course, and staff. */
    QuizResponse get(Long id);

    PageResponse<QuizResponse> list(Long courseId, Pageable pageable);

    /** Tests the signed-in student can see, with their attempts and best result. */
    List<StudentQuizResponse> availableToMe();
}
