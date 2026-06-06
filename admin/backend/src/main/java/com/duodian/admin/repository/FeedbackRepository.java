package com.duodian.admin.repository;

import com.duodian.admin.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByDeletedOrderByCreatedAtDesc(Byte deleted);
    Optional<Feedback> findByIdAndDeleted(Long id, Byte deleted);
    List<Feedback> findByUserIdAndDeletedOrderByCreatedAtDesc(Long userId, Byte deleted);
    List<Feedback> findByStatusAndDeletedOrderByCreatedAtDesc(String status, Byte deleted);
}
