package com.duodian.admin.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "shop_orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_shop_orders_platform_order",
                        columnNames = {"shop_id", "platform", "platform_order_id", "deleted"}
                )
        },
        indexes = {
                @Index(name = "idx_shop_orders_shop_id", columnList = "shop_id"),
                @Index(name = "idx_shop_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_shop_orders_channel_id", columnList = "channel_id"),
                @Index(name = "idx_shop_orders_platform", columnList = "platform"),
                @Index(name = "idx_shop_orders_status", columnList = "status"),
                @Index(name = "idx_shop_orders_completed_at", columnList = "completed_at"),
                @Index(name = "idx_shop_orders_last_seen_at", columnList = "last_seen_at"),
                @Index(name = "idx_shop_orders_deleted", columnList = "deleted")
        }
)
public class ShopOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "platform", nullable = false, length = 32)
    private String platform;

    @Column(name = "platform_name", length = 64)
    private String platformName;

    @Column(name = "platform_shop_id", length = 128)
    private String platformShopId;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Column(name = "platform_order_id", nullable = false, length = 128)
    private String platformOrderId;

    @Column(name = "platform_order_no", length = 64)
    private String platformOrderNo;

    @Column(name = "order_sequence", length = 64)
    private String orderSequence;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "order_time_text", length = 128)
    private String orderTimeText;

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;

    @Column(name = "expected_delivery_at")
    private LocalDateTime expectedDeliveryAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "status_text", length = 128)
    private String statusText;

    @Column(name = "order_type", length = 64)
    private String orderType;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "estimated_income", precision = 12, scale = 2)
    private BigDecimal estimatedIncome;

    @Column(name = "customer_paid_amount", precision = 12, scale = 2)
    private BigDecimal customerPaidAmount;

    @Column(name = "merchant_income", precision = 12, scale = 2)
    private BigDecimal merchantIncome;

    @Column(name = "original_amount", precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "delivery_fee", precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "package_fee", precision = 12, scale = 2)
    private BigDecimal packageFee;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "currency", length = 8)
    private String currency = "CNY";

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "customer_phone_tail", length = 16)
    private String customerPhoneTail;

    @Column(name = "privacy_phone", length = 64)
    private String privacyPhone;

    @Column(name = "backup_phone", length = 64)
    private String backupPhone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "recipient_address", columnDefinition = "TEXT")
    private String recipientAddress;

    @Column(name = "delivery_type", length = 64)
    private String deliveryType;

    @Column(name = "rider_name", length = 128)
    private String riderName;

    @Column(name = "rider_phone", length = 64)
    private String riderPhone;

    @Column(name = "remark", length = 512)
    private String remark;

    @Column(name = "item_summary", columnDefinition = "TEXT")
    private String itemSummary;

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "ingest_batch_id", length = 128)
    private String ingestBatchId;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (deleted == null) {
            deleted = 0;
        }
        if (currency == null || currency.isBlank()) {
            currency = "CNY";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (currency == null || currency.isBlank()) {
            currency = "CNY";
        }
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public String getPlatformShopId() { return platformShopId; }
    public void setPlatformShopId(String platformShopId) { this.platformShopId = platformShopId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getPlatformOrderId() { return platformOrderId; }
    public void setPlatformOrderId(String platformOrderId) { this.platformOrderId = platformOrderId; }

    public String getPlatformOrderNo() { return platformOrderNo; }
    public void setPlatformOrderNo(String platformOrderNo) { this.platformOrderNo = platformOrderNo; }

    public String getOrderSequence() { return orderSequence; }
    public void setOrderSequence(String orderSequence) { this.orderSequence = orderSequence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getOrderTimeText() { return orderTimeText; }
    public void setOrderTimeText(String orderTimeText) { this.orderTimeText = orderTimeText; }

    public LocalDateTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(LocalDateTime orderedAt) { this.orderedAt = orderedAt; }

    public LocalDateTime getExpectedDeliveryAt() { return expectedDeliveryAt; }
    public void setExpectedDeliveryAt(LocalDateTime expectedDeliveryAt) { this.expectedDeliveryAt = expectedDeliveryAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }

    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }

    public BigDecimal getEstimatedIncome() { return estimatedIncome; }
    public void setEstimatedIncome(BigDecimal estimatedIncome) { this.estimatedIncome = estimatedIncome; }

    public BigDecimal getCustomerPaidAmount() { return customerPaidAmount; }
    public void setCustomerPaidAmount(BigDecimal customerPaidAmount) { this.customerPaidAmount = customerPaidAmount; }

    public BigDecimal getMerchantIncome() { return merchantIncome; }
    public void setMerchantIncome(BigDecimal merchantIncome) { this.merchantIncome = merchantIncome; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getPackageFee() { return packageFee; }
    public void setPackageFee(BigDecimal packageFee) { this.packageFee = packageFee; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhoneTail() { return customerPhoneTail; }
    public void setCustomerPhoneTail(String customerPhoneTail) { this.customerPhoneTail = customerPhoneTail; }

    public String getPrivacyPhone() { return privacyPhone; }
    public void setPrivacyPhone(String privacyPhone) { this.privacyPhone = privacyPhone; }

    public String getBackupPhone() { return backupPhone; }
    public void setBackupPhone(String backupPhone) { this.backupPhone = backupPhone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }

    public String getDeliveryType() { return deliveryType; }
    public void setDeliveryType(String deliveryType) { this.deliveryType = deliveryType; }

    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }

    public String getRiderPhone() { return riderPhone; }
    public void setRiderPhone(String riderPhone) { this.riderPhone = riderPhone; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getItemSummary() { return itemSummary; }
    public void setItemSummary(String itemSummary) { this.itemSummary = itemSummary; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getIngestBatchId() { return ingestBatchId; }
    public void setIngestBatchId(String ingestBatchId) { this.ingestBatchId = ingestBatchId; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
