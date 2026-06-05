package com.duodian.admin.repository;

import com.duodian.admin.entity.PlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, Long> {
    List<PlatformConfig> findAllByOrderBySortOrderAscIdAsc();
    Optional<PlatformConfig> findByPlatformId(String platformId);
    boolean existsByPlatformId(String platformId);
    boolean existsByPlatformIdAndIdNot(String platformId, Long id);
}
