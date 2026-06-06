package com.duodian.admin.repository;

import com.duodian.admin.entity.TransactionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    List<TransactionLog> findByDeletedOrderByCreatedAtDesc(Byte deleted);
    java.util.Optional<TransactionLog> findByIdAndDeleted(Long id, Byte deleted);
    List<TransactionLog> findByUserIdAndDeletedOrderByCreatedAtDesc(Long userId, Byte deleted);
    List<TransactionLog> findByTypeAndDeletedOrderByCreatedAtDesc(String type, Byte deleted);

    @Query("SELECT t FROM TransactionLog t WHERE t.userId = :userId " +
           "AND t.deleted = :active " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:startDate IS NULL OR t.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR t.createdAt <= :endDate) " +
           "ORDER BY t.createdAt DESC")
    Page<TransactionLog> findByUserIdAndConditions(
            @Param("userId") Long userId,
            @Param("active") Byte active,
            @Param("type") String type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
