package com.itilms.admission.service;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.itilms.admission.dto.request.CreateTrainerRequest;
import com.itilms.admission.dto.request.UpdateTrainerRequest;
import com.itilms.admission.dto.response.TrainerResponse;
import com.itilms.admission.dto.response.TrainerSummaryResponse;
import com.itilms.common.dto.PageResponse;

public interface TrainerService {

    PageResponse<TrainerSummaryResponse> search(String status, String query, Pageable pageable);

    TrainerResponse get(Long id);

    TrainerResponse getByUserId(Long userId);

    TrainerResponse create(CreateTrainerRequest request);

    TrainerResponse update(Long id, UpdateTrainerRequest request);

    List<TrainerSummaryResponse> findByIds(Collection<Long> ids);

    /**
     * Trainers qualified for a course and currently available.
     * Populates the trainer picker when a coordinator creates a batch.
     */
    List<TrainerSummaryResponse> findAvailableForCourse(Long courseId);
}
