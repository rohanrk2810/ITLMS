package com.itilms.admission.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itilms.admission.entity.Student;
import com.itilms.admission.entity.StudentPhoto;
import com.itilms.admission.repository.StudentPhotoRepository;
import com.itilms.admission.repository.StudentRepository;
import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.exception.ResourceNotFoundException;
import com.itilms.common.security.AppPrincipal;
import com.itilms.common.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * The photo printed on a student's ID card.
 *
 * <p>A student adds the photo once, when they first sign in. After that it is
 * locked: only staff can replace it, so a card cannot be given a different face
 * by the person it names.
 */
@Service
@RequiredArgsConstructor
public class StudentPhotoService {

    private static final String SERVICE_NAME = "admission-service";
    static final int MAX_PHOTO_BYTES = 256 * 1024;

    private final StudentRepository students;
    private final StudentPhotoRepository photos;
    private final EventPublisher events;

    public record Photo(byte[] bytes, String contentType) {
    }

    /** The student's own first upload. Refused once a photo exists. */
    @Transactional
    public void setOwn(Long userId, byte[] bytes) {
        Student student = students.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile for user", userId));
        if (student.isHasPhoto()) {
            throw new BusinessRuleException(
                    "Your photo is already saved. Ask the institute office if it has to be changed.");
        }
        store(student, bytes, userId);
    }

    /** Staff add or replace the photo at any time. */
    @Transactional
    public void setByStaff(Long studentId, byte[] bytes) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        store(student, bytes, SecurityUtils.currentUserId());
    }

    /** Staff, or the student whose photo it is. */
    @Transactional(readOnly = true)
    public Photo get(Long studentId) {
        AppPrincipal caller = SecurityUtils.requirePrincipal();
        if (!caller.isStaff() && !(caller.isStudent() && studentId.equals(caller.profileId()))) {
            throw new ForbiddenOperationException("You can only see your own photo.");
        }
        StudentPhoto photo = photos.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("This student has no photo yet."));
        return new Photo(photo.getPhoto(), photo.getContentType());
    }

    private void store(Student student, byte[] bytes, Long actor) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("Choose a photo to upload.");
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw new BusinessRuleException("The photo is larger than 256 KB. Use a smaller picture.");
        }
        // The type comes from the bytes, not from what the browser claimed.
        String type = sniff(bytes);
        if (type == null) {
            throw new BusinessRuleException("The photo must be a JPEG or PNG picture.");
        }
        StudentPhoto photo = photos.findById(student.getId()).orElseGet(() -> {
            StudentPhoto fresh = new StudentPhoto();
            fresh.setStudentId(student.getId());
            return fresh;
        });
        photo.setPhoto(bytes);
        photo.setContentType(type);
        photo.setUpdatedBy(actor);
        photo.setUpdatedAt(Instant.now());
        photos.save(photo);
        student.setHasPhoto(true);
        students.save(student);
        events.audit(SERVICE_NAME, "STUDENT_PHOTO_SET", "Student", student.getId(), null,
                Map.of("studentCode", student.getStudentCode()));
    }

    /** JPEG or PNG by its first bytes; anything else is refused. */
    static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        return null;
    }
}
