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

    /** The active staff (every role except student): id, name, email and role. For the finance desk's payroll. */
    java.util.List<com.itilms.identity.dto.response.StaffEntry> staffDirectory();

    PageResponse<UserResponse> search(String role, String status, String query, Pageable pageable);

    UserResponse get(Long id);

    UserResponse create(CreateUserRequest request);

    UserResponse update(Long id, UpdateUserRequest request);

    UserResponse updateStatus(Long id, UpdateStatusRequest request);

    /**
     * Gives a person extra duties besides their main role, or withdraws them (an empty list). Administrator only. The
     * gateway is told at once, so the change applies on the person's next request.
     */
    UserResponse updateDuties(Long id, java.util.List<String> extraRoles);

    /** Sets or clears (null) the sidebar items hidden from one student. */
    UserResponse updateMenu(Long id, com.itilms.identity.dto.request.UserMenuRequest request);

    /**
     * Soft-deletes an account: it can no longer sign in, its sessions end and its name, e-mail and phone are wiped
     * (so they can be used again). History in other services stays, pointing at the same id.
     */
    void delete(Long id, String reason);

    /** Issues a temporary password and forces a change at next sign-in. */
    void resetPasswordAsAdmin(Long id);

    /** Bulk name resolution for other services. */
    List<UserSummaryResponse> findByIds(Collection<Long> ids);

    List<UserSummaryResponse> findActiveByRole(String role);

    /** Headline counts for the admin dashboard (Doc S15). */
    Map<String, Long> countsByRole();
}
