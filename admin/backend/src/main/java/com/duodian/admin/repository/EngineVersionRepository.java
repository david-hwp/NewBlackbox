package com.duodian.admin.repository;

import com.duodian.admin.entity.EngineVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EngineVersionRepository extends JpaRepository<EngineVersion, Long> {
    List<EngineVersion> findByDeletedOrderByVersionCodeDesc(Byte deleted);
    Optional<EngineVersion> findByIdAndDeleted(Long id, Byte deleted);
    List<EngineVersion> findByAvailableAndDeletedOrderByVersionCodeDesc(Boolean available, Byte deleted);
    Optional<EngineVersion> findByVersionCodeAndAvailableAndDeleted(Integer versionCode, Boolean available, Byte deleted);
}
