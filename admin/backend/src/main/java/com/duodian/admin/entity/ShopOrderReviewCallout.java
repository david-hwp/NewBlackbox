package com.duodian.admin.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "shop_order_review_callouts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_callout_order", columnNames = {"shop_order_id", "deleted"})
        },
        indexes = {
                @Index(name = "idx_review_callout_shop_id", columnList = "shop_id"),
                @Index(name = "idx_review_callout_user_id", columnList = "user_id"),
                @Index(name = "idx_review_callout_external_task", columnList = "external_task_id"),
                @Index(name = "idx_review_callout_status", columnList = "status"),
                @Index(name = "idx_review_callout_deleted", columnList = "deleted")
        }
)
public class ShopOrderReviewCallout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_order_id", nullable = false)
    private Long shopOrderId;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(length = 32)
    private String platform;

    @Column(name = "platform_shop_id", length = 128)
    private String platformShopId;

    @Column(name = "platform_order_id", length = 128)
    private String platformOrderId;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "phone_masked", length = 64)
    private String phoneMasked;

    @Column(name = "phone_hash", length = 128)
    private String phoneHash;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "external_task_id", length = 128)
    private String externalTaskId;

    @Column(name = "request_batch_id", length = 128)
    private String requestBatchId;

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
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getShopOrderId() { return shopOrderId; }
    public void setShopOrderId(Long shopOrderId) { this.shopOrderId = shopOrderId; }
    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getPlatformShopId() { return platformShopId; }
    public void setPlatformShopId(String platformShopId) { this.platformShopId = platformShopId; }
    public String getPlatformOrderId() { return platformOrderId; }
    public void setPlatformOrderId(String platformOrderId) { this.platformOrderId = platformOrderId; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getPhoneMasked() { return phoneMasked; }
    public void setPhoneMasked(String phoneMasked) { this.phoneMasked = phoneMasked; }
    public String getPhoneHash() { return phoneHash; }
    public void setPhoneHash(String phoneHash) { this.phoneHash = phoneHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExternalTaskId() { return externalTaskId; }
    public void setExternalTaskId(String externalTaskId) { this.externalTaskId = externalTaskId; }
    public String getRequestBatchId() { return requestBatchId; }
    public void setRequestBatchId(String requestBatchId) { this.requestBatchId = requestBatchId; }
    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
