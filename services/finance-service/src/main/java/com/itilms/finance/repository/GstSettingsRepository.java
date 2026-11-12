package com.itilms.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itilms.finance.entity.GstSettings;

public interface GstSettingsRepository extends JpaRepository<GstSettings, Short> {
}
