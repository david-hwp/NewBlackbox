package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "shops",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_clone_instance_id", columnNames = "clone_instance_id")
        }
)
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Column(nullable = false)
    private String platform; // meituan, taobao, jd, kuaishou, xiaohongshu, ali

    @Column(name = "platform_name")
    private String platformName;

    @Column(name = "remaining_days")
    private Integer remainingDays = 0;

    @Column(name = "auto_renew")
    private Boolean autoRenew = false;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "clone_instance_id", length = 96)
    private String cloneInstanceId;

    @Column(name = "last_deducted_at")
    private LocalDateTime lastDeductedAt;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

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
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Shop() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public Integer getRemainingDays() { return remainingDays; }
    public void setRemainingDays(Integer remainingDays) { this.remainingDays = remainingDays; }

    public Boolean getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getCloneInstanceId() { return cloneInstanceId; }
    public void setCloneInstanceId(String cloneInstanceId) {
        this.cloneInstanceId = (cloneInstanceId == null || cloneInstanceId.isBlank())
                ? null
                : cloneInstanceId.trim();
    }

    public LocalDateTime getLastDeductedAt() { return lastDeductedAt; }
    public void setLastDeductedAt(LocalDateTime lastDeductedAt) { this.lastDeductedAt = lastDeductedAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
