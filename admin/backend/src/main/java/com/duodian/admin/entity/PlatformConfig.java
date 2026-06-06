package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "platform_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_platform_id", columnNames = "platform_id")
        }
)
public class PlatformConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id", nullable = false, length = 64)
    private String platformId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "package_name", length = 256)
    private String packageName;

    @Column(name = "icon_url", length = 512)
    private String iconUrl;

    @Column(nullable = false)
    private Boolean available = false;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = normalize(platformId); }

    public String getName() { return name; }
    public void setName(String name) { this.name = normalize(name); }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = normalize(packageName); }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = normalize(iconUrl); }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = Boolean.TRUE.equals(available); }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder == null ? 0 : sortOrder; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
