package com.duodian.admin.repository;

import com.duodian.admin.entity.PlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, Long> {
    List<PlatformConfig> findByDeletedOrderBySortOrderAscIdAsc(Byte deleted);
    Optional<PlatformConfig> findByIdAndDeleted(Long id, Byte deleted);
    Optional<PlatformConfig> findByPlatformIdAndDeleted(String platformId, Byte deleted);
    boolean existsByPlatformIdAndDeleted(String platformId, Byte deleted);
    boolean existsByPlatformIdAndIdNotAndDeleted(String platformId, Long id, Byte deleted);
}
