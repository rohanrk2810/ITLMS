package com.itilms.identity.dto.response;

import com.itilms.identity.entity.User;

/** One member of staff, as the finance desk needs them for payroll. */
public record StaffEntry(Long id, String fullName, String email, String role, String roleDisplayName) {

    public static StaffEntry from(User u) {
        return new StaffEntry(u.getId(), u.fullName(), u.getEmail(), u.getRole().name(), u.getRole().displayName());
    }
}
