package com.duodian.admin.repository;

import com.duodian.admin.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByPublishedOrderByCreatedAtDesc(Boolean published);
}
