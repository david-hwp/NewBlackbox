package com.duodian.admin.repository;

import com.duodian.admin.entity.ShopOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
    Optional<ShopOrder> findByShopIdAndPlatformAndPlatformOrderIdAndDeleted(
            Long shopId,
            String platform,
            String platformOrderId,
            Byte deleted
    );

    @Query("""
            select o
            from ShopOrder o
            where o.deleted = :active
              and (:shopId is null or o.shopId = :shopId)
              and (:platform is null or o.platform = :platform)
              and (:status is null or o.status = :status or o.statusText like concat('%', :status, '%'))
              and (:shopName is null or o.shopName like concat('%', :shopName, '%'))
              and (:customerKeyword is null
                   or o.customerName like concat('%', :customerKeyword, '%')
                   or o.customerPhoneTail like concat('%', :customerKeyword, '%')
                   or o.privacyPhone like concat('%', :customerKeyword, '%')
                   or o.backupPhone like concat('%', :customerKeyword, '%')
                   or o.address like concat('%', :customerKeyword, '%')
                   or o.recipientAddress like concat('%', :customerKeyword, '%'))
              and (:orderKeyword is null
                   or o.platformOrderId like concat('%', :orderKeyword, '%')
                   or o.platformOrderNo like concat('%', :orderKeyword, '%')
                   or o.orderSequence like concat('%', :orderKeyword, '%'))
              and (:completedStart is null or o.completedAt >= :completedStart)
              and (:completedEnd is null or o.completedAt <= :completedEnd)
            """)
    Page<ShopOrder> searchOrders(
            @Param("active") Byte active,
            @Param("shopId") Long shopId,
            @Param("platform") String platform,
            @Param("status") String status,
            @Param("shopName") String shopName,
            @Param("customerKeyword") String customerKeyword,
            @Param("orderKeyword") String orderKeyword,
            @Param("completedStart") LocalDateTime completedStart,
            @Param("completedEnd") LocalDateTime completedEnd,
            Pageable pageable
    );
}
