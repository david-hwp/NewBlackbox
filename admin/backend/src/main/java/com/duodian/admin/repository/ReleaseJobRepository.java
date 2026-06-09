package com.duodian.admin.repository;

import com.duodian.admin.entity.ReleaseJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface ReleaseJobRepository extends JpaRepository<ReleaseJob, Long> {
    Optional<ReleaseJob> findByIdAndDeleted(Long id, Byte deleted);

    boolean existsByChannelIdAndStatusInAndDeleted(Long channelId, Collection<String> statuses, Byte deleted);

    @Query("""
            select count(j)
            from ReleaseJob j
            where j.deleted = :active
              and j.channelId = :channelId
              and j.status in :statuses
              and j.id <> :jobId
            """)
    long countActiveJobsForChannelExcluding(
            @Param("active") Byte active,
            @Param("channelId") Long channelId,
            @Param("statuses") Collection<String> statuses,
            @Param("jobId") Long jobId
    );

    @Query("""
            select j
            from ReleaseJob j
            where j.deleted = :active
              and (:channelId is null or j.channelId = :channelId)
              and (:status is null or j.status = :status)
            """)
    Page<ReleaseJob> searchReleaseJobs(
            @Param("active") Byte active,
            @Param("channelId") Long channelId,
            @Param("status") String status,
            Pageable pageable
    );
}
