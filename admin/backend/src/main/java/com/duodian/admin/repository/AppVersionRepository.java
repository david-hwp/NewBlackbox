package com.duodian.admin.repository;

import com.duodian.admin.entity.AppVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {
    List<AppVersion> findByDeletedOrderByVersionCodeDesc(Byte deleted);
    Optional<AppVersion> findByIdAndDeleted(Long id, Byte deleted);
    List<AppVersion> findByPublishedAndDeletedOrderByVersionCodeDesc(Boolean published, Byte deleted);
}
