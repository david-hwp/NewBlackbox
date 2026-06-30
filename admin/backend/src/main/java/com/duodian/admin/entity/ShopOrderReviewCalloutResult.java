package com.duodian.admin.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "shop_order_review_callout_results",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_callout_result_key", columnNames = {"callback_idempotency_key", "deleted"})
        },
        indexes = {
                @Index(name = "idx_review_callout_result_callout", columnList = "review_callout_id"),
                @Index(name = "idx_review_callout_result_order", columnList = "shop_order_id"),
                @Index(name = "idx_review_callout_result_shop", columnList = "shop_id"),
                @Index(name = "idx_review_callout_result_user", columnList = "user_id"),
                @Index(name = "idx_review_callout_result_billing", columnList = "billing_status"),
                @Index(name = "idx_review_callout_result_call_time", columnList = "call_time"),
                @Index(name = "idx_review_callout_result_task", columnList = "external_task_id"),
                @Index(name = "idx_review_callout_result_deleted", columnList = "deleted")
        }
)
public class ShopOrderReviewCalloutResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_callout_id")
    private Long reviewCalloutId;

    @Column(name = "shop_order_id")
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

    @Column(name = "external_cdr_id", length = 128)
    private String externalCdrId;

    @Column(name = "external_task_id", length = 128)
    private String externalTaskId;

    @Column(name = "callback_idempotency_key", nullable = false, length = 255)
    private String callbackIdempotencyKey;

    @Column(name = "phone_masked", length = 64)
    private String phoneMasked;

    @Column(name = "phone_hash", length = 128)
    private String phoneHash;

    @Column(name = "call_state", length = 64)
    private String callState;

    @Column(name = "call_state_text", length = 128)
    private String callStateText;

    private Boolean connected;

    @Column(name = "call_time")
    private LocalDateTime callTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "billed_minutes")
    private Integer billedMinutes;

    @Column(name = "external_money_cent")
    private Integer externalMoneyCent;

    private String grade;

    @Column(length = 512)
    private String remark;

    @Column(name = "billing_status", nullable = false, length = 32)
    private String billingStatus = "PENDING";

    @Column(name = "billable_reason", length = 128)
    private String billableReason;

    @Column(name = "deduction_amount")
    private Integer deductionAmount;

    @Column(name = "billing_rate_units_per_minute")
    private Integer billingRateUnitsPerMinute;

    @Column(name = "minimum_charge_units")
    private Integer minimumChargeUnits;

    @Column(name = "deducted_at")
    private LocalDateTime deductedAt;

    @Column(name = "transaction_log_id")
    private Long transactionLogId;

    @Column(name = "params_summary", columnDefinition = "TEXT")
    private String paramsSummary;

    @Column(name = "raw_summary", columnDefinition = "TEXT")
    private String rawSummary;

    @Column(name = "last_error", length = 512)
    private String lastError;

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
        if (billingStatus == null || billingStatus.isBlank()) {
            billingStatus = "PENDING";
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
    public Long getReviewCalloutId() { return reviewCalloutId; }
    public void setReviewCalloutId(Long reviewCalloutId) { this.reviewCalloutId = reviewCalloutId; }
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
    public String getExternalCdrId() { return externalCdrId; }
    public void setExternalCdrId(String externalCdrId) { this.externalCdrId = externalCdrId; }
    public String getExternalTaskId() { return externalTaskId; }
    public void setExternalTaskId(String externalTaskId) { this.externalTaskId = externalTaskId; }
    public String getCallbackIdempotencyKey() { return callbackIdempotencyKey; }
    public void setCallbackIdempotencyKey(String callbackIdempotencyKey) { this.callbackIdempotencyKey = callbackIdempotencyKey; }
    public String getPhoneMasked() { return phoneMasked; }
    public void setPhoneMasked(String phoneMasked) { this.phoneMasked = phoneMasked; }
    public String getPhoneHash() { return phoneHash; }
    public void setPhoneHash(String phoneHash) { this.phoneHash = phoneHash; }
    public String getCallState() { return callState; }
    public void setCallState(String callState) { this.callState = callState; }
    public String getCallStateText() { return callStateText; }
    public void setCallStateText(String callStateText) { this.callStateText = callStateText; }
    public Boolean getConnected() { return connected; }
    public void setConnected(Boolean connected) { this.connected = connected; }
    public LocalDateTime getCallTime() { return callTime; }
    public void setCallTime(LocalDateTime callTime) { this.callTime = callTime; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getBilledMinutes() { return billedMinutes; }
    public void setBilledMinutes(Integer billedMinutes) { this.billedMinutes = billedMinutes; }
    public Integer getExternalMoneyCent() { return externalMoneyCent; }
    public void setExternalMoneyCent(Integer externalMoneyCent) { this.externalMoneyCent = externalMoneyCent; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getBillingStatus() { return billingStatus; }
    public void setBillingStatus(String billingStatus) { this.billingStatus = billingStatus; }
    public String getBillableReason() { return billableReason; }
    public void setBillableReason(String billableReason) { this.billableReason = billableReason; }
    public Integer getDeductionAmount() { return deductionAmount; }
    public void setDeductionAmount(Integer deductionAmount) { this.deductionAmount = deductionAmount; }
    public Integer getBillingRateUnitsPerMinute() { return billingRateUnitsPerMinute; }
    public void setBillingRateUnitsPerMinute(Integer billingRateUnitsPerMinute) { this.billingRateUnitsPerMinute = billingRateUnitsPerMinute; }
    public Integer getMinimumChargeUnits() { return minimumChargeUnits; }
    public void setMinimumChargeUnits(Integer minimumChargeUnits) { this.minimumChargeUnits = minimumChargeUnits; }
    public LocalDateTime getDeductedAt() { return deductedAt; }
    public void setDeductedAt(LocalDateTime deductedAt) { this.deductedAt = deductedAt; }
    public Long getTransactionLogId() { return transactionLogId; }
    public void setTransactionLogId(Long transactionLogId) { this.transactionLogId = transactionLogId; }
    public String getParamsSummary() { return paramsSummary; }
    public void setParamsSummary(String paramsSummary) { this.paramsSummary = paramsSummary; }
    public String getRawSummary() { return rawSummary; }
    public void setRawSummary(String rawSummary) { this.rawSummary = rawSummary; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
