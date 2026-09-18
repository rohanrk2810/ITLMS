package com.itilms.identity.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.itilms.common.dto.PageResponse;
import com.itilms.identity.dto.request.CreateUserRequest;
import com.itilms.identity.dto.request.UpdateStatusRequest;
import com.itilms.identity.dto.request.UpdateUserRequest;
import com.itilms.identity.dto.response.UserResponse;
import com.itilms.identity.dto.response.UserSummaryResponse;

/** Account administration, as performed by staff rather than by the account holder. */
public interface UserService {

    PageResponse<UserResponse> search(String role, String status, String query, Pageable pageable);

    UserResponse get(Long id);

    UserResponse create(CreateUserRequest request);

    UserResponse update(Long id, UpdateUserRequest request);

    UserResponse updateStatus(Long id, UpdateStatusRequest request);

    /** Issues a temporary password and forces a change at next sign-in. */
    void resetPasswordAsAdmin(Long id);

    /** Bulk name resolution for other services. */
    List<UserSummaryResponse> findByIds(Collection<Long> ids);

    List<UserSummaryResponse> findActiveByRole(String role);

    /** Headline counts for the admin dashboard (Doc S15). */
    Map<String, Long> countsByRole();
}
