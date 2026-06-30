package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs")
public class TransactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "related_log_id")
    private Long relatedLogId;

    @Column(name = "shop_order_id")
    private Long shopOrderId;

    @Column(name = "review_callout_id")
    private Long reviewCalloutId;

    @Column(name = "external_task_id")
    private String externalTaskId;

    @Column(name = "external_cdr_id")
    private String externalCdrId;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "billing_rate")
    private Integer billingRate;

    @Column(nullable = false)
    private String type; // CONSUME, OUT, IN, PHONE_CONSUME, PHONE_OUT, PHONE_IN

    @Column(nullable = false)
    private Integer amount;

    private String platform;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "from_phone")
    private String fromPhone;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "to_phone")
    private String toPhone;

    @Column(name = "to_name")
    private String toName;

    private String remark;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (deleted == null) {
            deleted = 0;
        }
        createdAt = LocalDateTime.now();
    }

    public TransactionLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public Long getRelatedLogId() { return relatedLogId; }
    public void setRelatedLogId(Long relatedLogId) { this.relatedLogId = relatedLogId; }

    public Long getShopOrderId() { return shopOrderId; }
    public void setShopOrderId(Long shopOrderId) { this.shopOrderId = shopOrderId; }

    public Long getReviewCalloutId() { return reviewCalloutId; }
    public void setReviewCalloutId(Long reviewCalloutId) { this.reviewCalloutId = reviewCalloutId; }

    public String getExternalTaskId() { return externalTaskId; }
    public void setExternalTaskId(String externalTaskId) { this.externalTaskId = externalTaskId; }

    public String getExternalCdrId() { return externalCdrId; }
    public void setExternalCdrId(String externalCdrId) { this.externalCdrId = externalCdrId; }

    public LocalDateTime getCalledAt() { return calledAt; }
    public void setCalledAt(LocalDateTime calledAt) { this.calledAt = calledAt; }

    public Integer getBillingRate() { return billingRate; }
    public void setBillingRate(Integer billingRate) { this.billingRate = billingRate; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getFromPhone() { return fromPhone; }
    public void setFromPhone(String fromPhone) { this.fromPhone = fromPhone; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getToPhone() { return toPhone; }
    public void setToPhone(String toPhone) { this.toPhone = toPhone; }

    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
