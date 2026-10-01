package com.itilms.liveclass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.liveclass.entity.MonitoringSetting;

public interface MonitoringSettingRepository extends JpaRepository<MonitoringSetting, Long> {

    Optional<MonitoringSetting> findByScopeTypeAndScopeId(MonitoringSetting.Scope scopeType, Long scopeId);

    List<MonitoringSetting> findAllByOrderByScopeTypeAscScopeIdAsc();
}
