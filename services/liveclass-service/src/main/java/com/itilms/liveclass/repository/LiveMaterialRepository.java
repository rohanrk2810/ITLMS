package com.itilms.liveclass.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.liveclass.entity.LiveMaterial;

public interface LiveMaterialRepository extends JpaRepository<LiveMaterial, Long> {

    /** What a trainer can see: their own, and everything other trainers have shared. */
    List<LiveMaterial> findByOwnerUserIdOrSharedTrueOrderByCreatedAtDesc(Long ownerUserId);
}
