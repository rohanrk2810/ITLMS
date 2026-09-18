package com.itilms.course.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.itilms.common.dto.PageResponse;
import com.itilms.course.dto.request.CreateCourseRequest;
import com.itilms.course.dto.request.UpdateCourseRequest;
import com.itilms.course.dto.response.CourseDetailResponse;
import com.itilms.course.dto.response.CourseResponse;
import com.itilms.course.dto.response.CourseSummaryResponse;

/** The course catalog and its curriculum (Doc S6.5, S6.8). */
public interface CourseService {

    /** Staff view: every course, any status. */
    PageResponse<CourseSummaryResponse> search(String status, String level, String query, Pageable pageable);

    /** Public catalog: published courses only, no authentication required. */
    PageResponse<CourseSummaryResponse> publicCatalog(String level, String query, Pageable pageable);

    /**
     * A course with its curriculum, shaped for whoever is asking.
     *
     * <p>Lesson content is included only for staff, or for a student with an
     * active enrolment, or for lessons flagged as previews. Everyone else sees
     * the outline with the material withheld.
     */
    CourseDetailResponse getDetail(Long courseId);

    CourseResponse get(Long courseId);

    CourseResponse create(CreateCourseRequest request);

    CourseResponse update(Long courseId, UpdateCourseRequest request);

    /**
     * Makes a course visible to students.
     *
     * <p>Refuses if the metadata the catalog page needs is missing, or if the
     * course has no lessons — Doc S14 requires "at least one module/lesson".
     */
    CourseResponse publish(Long courseId);

    CourseResponse archive(Long courseId);

    /** Bulk lookup for batch-service, finance-service and reporting. */
    List<CourseSummaryResponse> findByIds(Collection<Long> ids);

    Map<String, Long> counts();
}
