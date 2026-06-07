package com.duodian.admin.repository;

import com.duodian.admin.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByDeletedOrderByCreatedAtDesc(Byte deleted);
    Optional<Announcement> findByIdAndDeleted(Long id, Byte deleted);
    List<Announcement> findByPublishedAndDeletedOrderByCreatedAtDesc(Boolean published, Byte deleted);
    List<Announcement> findByTypeAndDeletedOrderByCreatedAtDesc(String type, Byte deleted);
    List<Announcement> findByPublishedAndTypeAndDeletedOrderByCreatedAtDesc(Boolean published, String type, Byte deleted);

    @Query("""
            select a
            from Announcement a
            where a.deleted = :active
              and (:title is null or a.title like concat('%', :title, '%'))
              and (:type is null or a.type = :type)
              and (:published is null or a.published = :published)
            """)
    Page<Announcement> searchAnnouncements(
            @Param("active") Byte active,
            @Param("title") String title,
            @Param("type") String type,
            @Param("published") Boolean published,
            Pageable pageable
    );
}
