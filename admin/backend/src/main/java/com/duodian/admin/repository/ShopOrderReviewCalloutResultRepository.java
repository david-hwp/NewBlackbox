package com.duodian.admin.repository;

import com.duodian.admin.entity.ShopOrderReviewCalloutResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShopOrderReviewCalloutResultRepository extends JpaRepository<ShopOrderReviewCalloutResult, Long> {
    Optional<ShopOrderReviewCalloutResult> findByCallbackIdempotencyKeyAndDeleted(String callbackIdempotencyKey, Byte deleted);
    Optional<ShopOrderReviewCalloutResult> findByExternalCdrIdAndDeleted(String externalCdrId, Byte deleted);
}
