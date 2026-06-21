package com.duodian.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_channel_id", columnList = "channel_id"),
                @Index(name = "idx_users_phone", columnList = "phone"),
                @Index(name = "idx_users_role", columnList = "role"),
                @Index(name = "idx_users_deleted", columnList = "deleted")
        }
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    private String role = "USER"; // SUPER_ADMIN / CHANNEL / USER, legacy ADMIN maps to SUPER_ADMIN

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "compute_balance")
    private Integer computeBalance = 0;

    @Column(name = "non_transferable_compute_balance")
    private Integer nonTransferableComputeBalance = 0;

    @Column(name = "phone_minutes_balance")
    private Integer phoneMinutesBalance = 0;

    @Column(name = "shop_count")
    private Integer shopCount = 0;

    @Column(name = "platform_count")
    private Integer platformCount = 0;

    @Column(name = "apk_channel", length = 64)
    private String apkChannel = "main";

    @Column(name = "subscription_plan", length = 32)
    private String subscriptionPlan = "NONE";

    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;

    @Column(name = "subscription_updated_at")
    private LocalDateTime subscriptionUpdatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "legacy_engine_migrated", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean legacyEngineMigrated = false;

    @Column(name = "legacy_engine_migrated_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime legacyEngineMigratedAt;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

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

    public User() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = normalizeRole(role); }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public Integer getComputeBalance() { return computeBalance; }
    public void setComputeBalance(Integer computeBalance) { this.computeBalance = computeBalance; }

    public Integer getNonTransferableComputeBalance() { return nonTransferableComputeBalance; }
    public void setNonTransferableComputeBalance(Integer nonTransferableComputeBalance) { this.nonTransferableComputeBalance = nonTransferableComputeBalance; }

    public Integer getPhoneMinutesBalance() { return phoneMinutesBalance; }
    public void setPhoneMinutesBalance(Integer phoneMinutesBalance) { this.phoneMinutesBalance = phoneMinutesBalance; }

    public Integer getShopCount() { return shopCount; }
    public void setShopCount(Integer shopCount) { this.shopCount = shopCount; }

    public Integer getPlatformCount() { return platformCount; }
    public void setPlatformCount(Integer platformCount) { this.platformCount = platformCount; }

    public String getApkChannel() { return apkChannel; }
    public void setApkChannel(String apkChannel) { this.apkChannel = apkChannel; }

    public String getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(String subscriptionPlan) {
        this.subscriptionPlan = (subscriptionPlan == null || subscriptionPlan.isBlank())
                ? "NONE"
                : subscriptionPlan.trim().toUpperCase();
    }

    public LocalDateTime getSubscriptionExpiresAt() { return subscriptionExpiresAt; }
    public void setSubscriptionExpiresAt(LocalDateTime subscriptionExpiresAt) { this.subscriptionExpiresAt = subscriptionExpiresAt; }

    public LocalDateTime getSubscriptionUpdatedAt() { return subscriptionUpdatedAt; }
    public void setSubscriptionUpdatedAt(LocalDateTime subscriptionUpdatedAt) { this.subscriptionUpdatedAt = subscriptionUpdatedAt; }

    public Boolean getSubscriptionActive() {
        return isSubscriptionActive();
    }

    @JsonIgnore
    public boolean isSubscriptionActive() {
        return subscriptionExpiresAt != null && subscriptionExpiresAt.isAfter(LocalDateTime.now());
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Boolean getLegacyEngineMigrated() { return Boolean.TRUE.equals(legacyEngineMigrated); }
    public void setLegacyEngineMigrated(Boolean legacyEngineMigrated) {
        this.legacyEngineMigrated = Boolean.TRUE.equals(legacyEngineMigrated);
    }

    public LocalDateTime getLegacyEngineMigratedAt() { return legacyEngineMigratedAt; }
    public void setLegacyEngineMigratedAt(LocalDateTime legacyEngineMigratedAt) {
        this.legacyEngineMigratedAt = legacyEngineMigratedAt;
    }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        String normalized = role.trim().toUpperCase();
        return "ADMIN".equals(normalized) ? "SUPER_ADMIN" : normalized;
    }
}
