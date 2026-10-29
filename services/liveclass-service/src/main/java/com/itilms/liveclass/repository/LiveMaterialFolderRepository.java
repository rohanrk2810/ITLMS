package com.itilms.liveclass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.liveclass.entity.LiveMaterialFolder;

public interface LiveMaterialFolderRepository extends JpaRepository<LiveMaterialFolder, Long> {

    List<LiveMaterialFolder> findByOwnerUserId(Long ownerUserId);

    Optional<LiveMaterialFolder> findByOwnerUserIdAndPath(Long ownerUserId, String path);
}
