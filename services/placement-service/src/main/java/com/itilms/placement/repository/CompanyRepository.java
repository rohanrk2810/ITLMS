package com.itilms.placement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itilms.placement.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByActiveTrueOrderByNameAsc();

    List<Company> findAllByOrderByNameAsc();

    /** Matches the database's case- and space-insensitive unique index. */
    @Query("SELECT COUNT(c) > 0 FROM Company c WHERE LOWER(TRIM(c.name)) = LOWER(TRIM(:name)) AND (:excludeId IS NULL OR c.id <> :excludeId)")
    boolean nameTaken(@Param("name") String name, @Param("excludeId") Long excludeId);
}
