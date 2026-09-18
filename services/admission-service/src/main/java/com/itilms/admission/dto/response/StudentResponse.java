package com.itilms.admission.dto.response;

import java.time.Instant;
import java.time.LocalDate;

import com.itilms.admission.entity.Student;

import io.swagger.v3.oas.annotations.media.Schema;

/** A full student profile. */
@Schema(description = "Student profile")
public record StudentResponse(
        Long id,
        Long userId,
        String studentCode,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String highestEducation,
        String college,
        Integer graduationYear,
        String addressLine,
        String city,
        String state,
        String pincode,
        String guardianName,
        String guardianPhone,
        String emergencyContact,
        LocalDate admissionDate,
        String admissionSource,
        String status,
        String remarks,
        Instant createdAt
) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getUserId(),
                student.getStudentCode(),
                student.getFullName(),
                student.getEmail(),
                student.getPhone(),
                student.getDateOfBirth(),
                student.getGender() == null ? null : student.getGender().name(),
                student.getHighestEducation(),
                student.getCollege(),
                student.getGraduationYear(),
                student.getAddressLine(),
                student.getCity(),
                student.getState(),
                student.getPincode(),
                student.getGuardianName(),
                student.getGuardianPhone(),
                student.getEmergencyContact(),
                student.getAdmissionDate(),
                student.getAdmissionSource() == null ? null : student.getAdmissionSource().name(),
                student.getStatus().name(),
                student.getRemarks(),
                student.getCreatedAt());
    }
}
