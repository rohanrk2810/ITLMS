package com.itilms.reporting.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.common.security.Roles;
import com.itilms.reporting.progress.StudentProgressReport;
import com.itilms.reporting.service.StudentProgressService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** A student's complete record and progress report, with suggestions for what to do next. */
@Tag(name = "Student progress", description = "A student's record, progress and suggestions")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class StudentProgressController {

    private final StudentProgressService service;

    @Operation(summary = "My progress report",
            description = "Profile, batches, attendance, live classes, recorded lessons, tests, coding and assignments, "
                    + "with suggestions. Test results a trainer has not released are held back.")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/me/progress")
    public StudentProgressReport mine() {
        return service.mine();
    }

    @Operation(summary = "A student's progress report",
            description = "Administrators and coordinators: any student. Trainers: only students in a batch they teach.")
    @PreAuthorize(Roles.ACADEMIC)
    @GetMapping("/students/{studentId}/progress")
    public StudentProgressReport of(@PathVariable Long studentId) {
        return service.of(studentId);
    }
}
