package com.duodian.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    private String role = "USER"; // ADMIN / USER

    @Column(name = "compute_balance")
    private Integer computeBalance = 0;

    @Column(name = "non_transferable_compute_balance")
    private Integer nonTransferableComputeBalance = 0;

    @Column(name = "shop_count")
    private Integer shopCount = 0;

    @Column(name = "platform_count")
    private Integer platformCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

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
    public void setRole(String role) { this.role = role; }

    public Integer getComputeBalance() { return computeBalance; }
    public void setComputeBalance(Integer computeBalance) { this.computeBalance = computeBalance; }

    public Integer getNonTransferableComputeBalance() { return nonTransferableComputeBalance; }
    public void setNonTransferableComputeBalance(Integer nonTransferableComputeBalance) { this.nonTransferableComputeBalance = nonTransferableComputeBalance; }

    public Integer getShopCount() { return shopCount; }
    public void setShopCount(Integer shopCount) { this.shopCount = shopCount; }

    public Integer getPlatformCount() { return platformCount; }
    public void setPlatformCount(Integer platformCount) { this.platformCount = platformCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }
}
