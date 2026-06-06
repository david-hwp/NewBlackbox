package com.duodian.admin.repository;

import com.duodian.admin.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByDeletedOrderByCreatedAtDesc(Byte deleted);
    Optional<Announcement> findByIdAndDeleted(Long id, Byte deleted);
    List<Announcement> findByPublishedAndDeletedOrderByCreatedAtDesc(Boolean published, Byte deleted);
}
