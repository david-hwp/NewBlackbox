package com.duodian.admin.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "channels",
        indexes = {
                @Index(name = "idx_channels_code", columnList = "code"),
                @Index(name = "idx_channels_deleted", columnList = "deleted"),
                @Index(name = "idx_channels_status", columnList = "status")
        }
)
public class Channel {
    public static final String MAIN_CODE = "main";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "app_display_name", length = 128)
    private String appDisplayName;

    @Column(name = "app_application_id", nullable = false, length = 128)
    private String appApplicationId;

    @Column(name = "app_icon_file_name", length = 255)
    private String appIconFileName;

    @Column(name = "app_icon_url", length = 512)
    private String appIconUrl;

    @Column(name = "app_icon_checksum", length = 128)
    private String appIconChecksum;

    @Column(name = "engine_display_name", length = 128)
    private String engineDisplayName;

    @Column(name = "engine_application_id", nullable = false, length = 128)
    private String engineApplicationId;

    @Column(name = "engine_icon_file_name", length = 255)
    private String engineIconFileName;

    @Column(name = "engine_icon_url", length = 512)
    private String engineIconUrl;

    @Column(name = "engine_icon_checksum", length = 128)
    private String engineIconChecksum;

    @Column(name = "engine_notification_title", length = 128)
    private String engineNotificationTitle;

    @Column(name = "engine_notification_text", length = 255)
    private String engineNotificationText;

    @Column(name = "register_bonus_compute", nullable = false)
    private Integer registerBonusCompute = 3;

    @Column(length = 512)
    private String remark;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        normalize();
        if (deleted == null) {
            deleted = 0;
        }
        if (registerBonusCompute == null || registerBonusCompute < 0) {
            registerBonusCompute = 0;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        normalize();
        if (registerBonusCompute == null || registerBonusCompute < 0) {
            registerBonusCompute = 0;
        }
        updatedAt = LocalDateTime.now();
    }

    private void normalize() {
        code = normalizeLower(code);
        name = normalizeRequired(name, code);
        status = normalizeStatus(status);
        appDisplayName = normalize(appDisplayName);
        appApplicationId = normalizeRequired(appApplicationId, "com.zhirang.zhanghaoguanjia");
        appIconFileName = normalize(appIconFileName);
        appIconUrl = normalize(appIconUrl);
        appIconChecksum = normalize(appIconChecksum);
        engineDisplayName = normalize(engineDisplayName);
        engineApplicationId = normalizeRequired(engineApplicationId, "com.zhirang.zhanghaoguanjia.engine");
        engineIconFileName = normalize(engineIconFileName);
        engineIconUrl = normalize(engineIconUrl);
        engineIconChecksum = normalize(engineIconChecksum);
        engineNotificationTitle = normalize(engineNotificationTitle);
        engineNotificationText = normalize(engineNotificationText);
        remark = normalize(remark);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equalsIgnoreCase(status) && deleted != null && deleted == 0;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return STATUS_ACTIVE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return STATUS_DISABLED.equals(normalized) ? STATUS_DISABLED : STATUS_ACTIVE;
    }

    private String normalizeLower(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = normalizeLower(code); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = normalizeStatus(status); }

    public Long getAdminUserId() { return adminUserId; }
    public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }

    public String getAppDisplayName() { return appDisplayName; }
    public void setAppDisplayName(String appDisplayName) { this.appDisplayName = appDisplayName; }

    public String getAppApplicationId() { return appApplicationId; }
    public void setAppApplicationId(String appApplicationId) { this.appApplicationId = appApplicationId; }

    public String getAppIconFileName() { return appIconFileName; }
    public void setAppIconFileName(String appIconFileName) { this.appIconFileName = appIconFileName; }

    public String getAppIconUrl() { return appIconUrl; }
    public void setAppIconUrl(String appIconUrl) { this.appIconUrl = appIconUrl; }

    public String getAppIconChecksum() { return appIconChecksum; }
    public void setAppIconChecksum(String appIconChecksum) { this.appIconChecksum = appIconChecksum; }

    public String getEngineDisplayName() { return engineDisplayName; }
    public void setEngineDisplayName(String engineDisplayName) { this.engineDisplayName = engineDisplayName; }

    public String getEngineApplicationId() { return engineApplicationId; }
    public void setEngineApplicationId(String engineApplicationId) { this.engineApplicationId = engineApplicationId; }

    public String getEngineIconFileName() { return engineIconFileName; }
    public void setEngineIconFileName(String engineIconFileName) { this.engineIconFileName = engineIconFileName; }

    public String getEngineIconUrl() { return engineIconUrl; }
    public void setEngineIconUrl(String engineIconUrl) { this.engineIconUrl = engineIconUrl; }

    public String getEngineIconChecksum() { return engineIconChecksum; }
    public void setEngineIconChecksum(String engineIconChecksum) { this.engineIconChecksum = engineIconChecksum; }

    public String getEngineNotificationTitle() { return engineNotificationTitle; }
    public void setEngineNotificationTitle(String engineNotificationTitle) { this.engineNotificationTitle = engineNotificationTitle; }

    public String getEngineNotificationText() { return engineNotificationText; }
    public void setEngineNotificationText(String engineNotificationText) { this.engineNotificationText = engineNotificationText; }

    public Integer getRegisterBonusCompute() { return registerBonusCompute; }
    public void setRegisterBonusCompute(Integer registerBonusCompute) { this.registerBonusCompute = registerBonusCompute; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
