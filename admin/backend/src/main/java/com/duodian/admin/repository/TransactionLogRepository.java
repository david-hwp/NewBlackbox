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
import java.util.Optional;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    List<TransactionLog> findByDeletedOrderByCreatedAtDesc(Byte deleted);
    Optional<TransactionLog> findByIdAndDeleted(Long id, Byte deleted);
    List<TransactionLog> findByUserIdAndDeletedOrderByCreatedAtDesc(Long userId, Byte deleted);
    List<TransactionLog> findByTypeAndDeletedOrderByCreatedAtDesc(String type, Byte deleted);

    Optional<TransactionLog> findFirstByUserIdAndTypeAndToPhoneAndRelatedLogIdIsNullAndRemarkStartingWithAndDeletedOrderByCreatedAtDescIdDesc(
            Long userId,
            String type,
            String toPhone,
            String remarkPrefix,
            Byte deleted
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from TransactionLog t
            where t.userId = :userId
              and t.deleted = :active
              and t.type = 'CONSUME'
              and (t.createdAt > :createdAt or (t.createdAt = :createdAt and t.id > :logId))
            """)
    Integer sumConsumedAfter(
            @Param("userId") Long userId,
            @Param("active") Byte active,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("logId") Long logId
    );

    @Query("""
            select coalesce(sum(t.amount), 0)
            from TransactionLog t
            where t.userId = :userId
              and t.deleted = :active
              and t.type = 'IN'
              and t.relatedLogId = :relatedLogId
            """)
    Integer sumReclaimedForSourceLog(
            @Param("userId") Long userId,
            @Param("active") Byte active,
            @Param("relatedLogId") Long relatedLogId
    );

    @Query("""
            select t
            from TransactionLog t
            left join User u on u.id = t.userId and u.deleted = :active
            where t.deleted = :active
              and (:userId is null or t.userId = :userId)
              and (:type is null or t.type = :type)
              and (:phone is null or t.fromPhone like concat('%', :phone, '%')
                   or t.toPhone like concat('%', :phone, '%')
                   or u.phone like concat('%', :phone, '%'))
              and (:shopName is null or t.shopName like concat('%', :shopName, '%'))
            """)
    Page<TransactionLog> searchLogs(
            @Param("active") Byte active,
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("phone") String phone,
            @Param("shopName") String shopName,
            Pageable pageable
    );

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
