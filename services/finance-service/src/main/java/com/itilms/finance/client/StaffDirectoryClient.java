package com.itilms.finance.client;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

/** The active staff (everyone but students), from the service that owns accounts. */
@FeignClient(name = "identity-service", contextId = "financeStaff", fallbackFactory = StaffDirectoryClient.Fallback.class)
public interface StaffDirectoryClient {

    @GetMapping("/api/users/staff-directory")
    List<Staff> staff();

    record Staff(Long id, String fullName, String email, String role, String roleDisplayName) {
    }

    /** No answer: the desk sees the people already on payroll, and is told the list could not be refreshed. */
    @Component
    class Fallback implements FallbackFactory<StaffDirectoryClient> {
        @Override
        public StaffDirectoryClient create(Throwable cause) {
            return () -> null;
        }
    }
}
