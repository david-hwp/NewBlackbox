package com.duodian.admin.repository;

import com.duodian.admin.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByDeletedOrderByCreatedAtDesc(Byte deleted);
    Optional<Feedback> findByIdAndDeleted(Long id, Byte deleted);
    List<Feedback> findByUserIdAndDeletedOrderByCreatedAtDesc(Long userId, Byte deleted);
    List<Feedback> findByStatusAndDeletedOrderByCreatedAtDesc(String status, Byte deleted);

    @Query("""
            select f
            from Feedback f
            where f.deleted = :active
              and (:userPhone is null or f.userPhone like concat('%', :userPhone, '%'))
              and (:status is null or f.status = :status)
              and (:content is null or f.content like concat('%', :content, '%')
                   or f.logCaption like concat('%', :content, '%')
                   or f.deviceInfo like concat('%', :content, '%'))
            """)
    Page<Feedback> searchFeedbacks(
            @Param("active") Byte active,
            @Param("userPhone") String userPhone,
            @Param("status") String status,
            @Param("content") String content,
            Pageable pageable
    );
}
