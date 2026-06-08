package com.duodian.admin.repository;

import com.duodian.admin.entity.EngineVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EngineVersionRepository extends JpaRepository<EngineVersion, Long> {
    List<EngineVersion> findByDeletedOrderByVersionCodeDesc(Byte deleted);
    Optional<EngineVersion> findByIdAndDeleted(Long id, Byte deleted);
    List<EngineVersion> findByAvailableAndDeletedOrderByVersionCodeDesc(Boolean available, Byte deleted);
    Optional<EngineVersion> findByVersionCodeAndAvailableAndDeleted(Integer versionCode, Boolean available, Byte deleted);

    @Query("""
            select v
            from EngineVersion v
            where v.deleted = :active
              and (:versionCode is null or v.versionCode = :versionCode)
              and (:versionName is null or v.versionName like concat('%', :versionName, '%'))
              and (:available is null or v.available = :available)
            """)
    Page<EngineVersion> searchEngineVersions(
            @Param("active") Byte active,
            @Param("versionCode") Integer versionCode,
            @Param("versionName") String versionName,
            @Param("available") Boolean available,
            Pageable pageable
    );
}
