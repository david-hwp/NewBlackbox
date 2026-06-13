package com.duodian.admin.repository;

import com.duodian.admin.entity.AppVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {
    List<AppVersion> findByDeletedOrderByVersionCodeDesc(Byte deleted);
    Optional<AppVersion> findByIdAndDeleted(Long id, Byte deleted);
    List<AppVersion> findByPublishedAndDeletedOrderByVersionCodeDesc(Boolean published, Byte deleted);
    List<AppVersion> findByChannelIdAndPublishedAndDeletedOrderByVersionCodeDesc(Long channelId, Boolean published, Byte deleted);
    List<AppVersion> findByChannelIdAndDeletedOrderByVersionCodeDesc(Long channelId, Byte deleted);
    Optional<AppVersion> findByVersionCodeAndPublishedAndDeleted(Integer versionCode, Boolean published, Byte deleted);
    Optional<AppVersion> findByChannelIdAndVersionCodeAndPublishedAndDeleted(Long channelId, Integer versionCode, Boolean published, Byte deleted);
    Optional<AppVersion> findByChannelIdAndVersionCodeAndDeleted(Long channelId, Integer versionCode, Byte deleted);

    @Query("""
            select v
            from AppVersion v
            where v.deleted = :active
              and (:channelId is null or v.channelId = :channelId)
              and (:versionCode is null or v.versionCode = :versionCode)
              and (:versionName is null or v.versionName like concat('%', :versionName, '%'))
              and (:published is null or v.published = :published)
            """)
    Page<AppVersion> searchAppVersions(
            @Param("active") Byte active,
            @Param("channelId") Long channelId,
            @Param("versionCode") Integer versionCode,
            @Param("versionName") String versionName,
            @Param("published") Boolean published,
            Pageable pageable
    );
}
