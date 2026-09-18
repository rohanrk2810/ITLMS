package com.itilms.assessment.service;

import com.itilms.assessment.dto.response.CompletionResponse;
import com.itilms.assessment.dto.response.MyResultsResponse;

/** Results across tests and assignments (Doc S8.2 "Results", S7.3). */
public interface ResultService {

    /** Everything the signed-in student has been assessed on (Doc S11: GET /api/results/me). */
    MyResultsResponse myResults();

    /**
     * Whether a student has done the assessed work their batch requires.
     *
     * <p>The assessment half of the completion rule in Doc S7.3. certificate-
     * service combines it with lesson progress and attendance before issuing.
     */
    CompletionResponse completion(Long studentId, Long courseId, Long batchId);
}
