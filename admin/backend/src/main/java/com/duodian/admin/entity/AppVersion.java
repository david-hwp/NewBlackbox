package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_versions")
public class AppVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false)
    private Integer versionCode;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "version_name", nullable = false, length = 64)
    private String versionName;

    @Column(name = "application_id", nullable = false, length = 128)
    private String applicationId = "com.zhirang.zhanghaoguanjia";

    @Column(name = "apk_url", nullable = false, length = 512)
    private String apkUrl;

    @Column(length = 64)
    private String checksum;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(columnDefinition = "TEXT")
    private String changelog;

    @Column(nullable = false)
    private Boolean published = true;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        applicationId = normalizeRequired(applicationId, "com.zhirang.zhanghaoguanjia");
        if (deleted == null) {
            deleted = 0;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        applicationId = normalizeRequired(applicationId, "com.zhirang.zhanghaoguanjia");
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getVersionCode() { return versionCode; }
    public void setVersionCode(Integer versionCode) { this.versionCode = versionCode; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = normalize(versionName); }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = normalizeRequired(applicationId, "com.zhirang.zhanghaoguanjia"); }

    public String getApkUrl() { return apkUrl; }
    public void setApkUrl(String apkUrl) { this.apkUrl = normalize(apkUrl); }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = normalize(checksum); }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = normalize(changelog); }

    public Boolean getPublished() { return published; }
    public void setPublished(Boolean published) { this.published = Boolean.TRUE.equals(published); }

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

    private String normalizeRequired(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }
}
