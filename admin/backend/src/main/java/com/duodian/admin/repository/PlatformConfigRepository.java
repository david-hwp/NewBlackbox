package com.duodian.admin.repository;

import com.duodian.admin.entity.PlatformConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    long countByDeleted(Byte deleted);

    @Query("""
            select p
            from PlatformConfig p
            where p.deleted = :active
              and (:name is null or p.name like concat('%', :name, '%'))
              and (:platformId is null or p.platformId like concat('%', :platformId, '%'))
              and (:packageName is null or p.packageName like concat('%', :packageName, '%'))
            """)
    Page<PlatformConfig> searchPlatforms(
            @Param("active") Byte active,
            @Param("name") String name,
            @Param("platformId") String platformId,
            @Param("packageName") String packageName,
            Pageable pageable
    );
}
