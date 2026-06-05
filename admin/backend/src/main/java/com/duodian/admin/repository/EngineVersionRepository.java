package com.duodian.admin.repository;

import com.duodian.admin.entity.EngineVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EngineVersionRepository extends JpaRepository<EngineVersion, Long> {
    List<EngineVersion> findByAvailableOrderByVersionCodeDesc(Boolean available);
}
