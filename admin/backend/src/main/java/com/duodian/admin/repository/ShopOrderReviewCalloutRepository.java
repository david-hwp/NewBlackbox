package com.duodian.admin.repository;

import com.duodian.admin.entity.ShopOrderReviewCallout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopOrderReviewCalloutRepository extends JpaRepository<ShopOrderReviewCallout, Long> {
    Optional<ShopOrderReviewCallout> findByIdAndDeleted(Long id, Byte deleted);
    Optional<ShopOrderReviewCallout> findByShopOrderIdAndDeleted(Long shopOrderId, Byte deleted);
}
