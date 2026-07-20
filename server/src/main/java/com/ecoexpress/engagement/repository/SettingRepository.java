package com.ecoexpress.engagement.repository;

import com.ecoexpress.engagement.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingRepository extends JpaRepository<Setting, UUID> {

    Optional<Setting> findByKey(String key);

    List<Setting> findByIsPublicTrue();
}
