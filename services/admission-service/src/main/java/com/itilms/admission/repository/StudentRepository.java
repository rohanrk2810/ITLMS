package com.itilms.admission.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itilms.admission.entity.Student;
import com.itilms.admission.entity.StudentStatus;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByUserId(Long userId);

    Optional<Student> findByStudentCode(String studentCode);

    boolean existsByUserId(Long userId);

    List<Student> findByIdIn(Collection<Long> ids);

    long countByStatus(StudentStatus status);

    /**
     * Next value for the student code sequence.
     *
     * <p>A dedicated database sequence would be cleaner, but the code embeds the
     * calendar year ({@code STU-2026-000123}) and must restart each January.
     * Counting this year's rows gives that for free; the unique constraint on
     * {@code student_code} catches the rare collision between two admissions
     * created in the same instant, and the caller retries.
     */
    @Query("SELECT COUNT(s) + 1 FROM Student s WHERE YEAR(s.admissionDate) = :year")
    long nextCodeSequence(@Param("year") int year);

    /** Applied when identity-service reports a name or contact change. */
    @Modifying
    @Query("""
            UPDATE Student s
               SET s.fullName = :fullName, s.email = :email, s.phone = :phone
             WHERE s.userId = :userId
            """)
    int syncIdentity(@Param("userId") Long userId,
                     @Param("fullName") String fullName,
                     @Param("email") String email,
                     @Param("phone") String phone);

    @Query("SELECT s.userId FROM Student s WHERE s.id IN :ids")
    List<Long> findUserIdsByIds(@Param("ids") Collection<Long> ids);
}
