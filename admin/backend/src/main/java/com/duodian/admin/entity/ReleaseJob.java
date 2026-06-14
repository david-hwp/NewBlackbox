package com.duodian.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "release_jobs",
        indexes = {
                @Index(name = "idx_release_jobs_channel_id", columnList = "channel_id"),
                @Index(name = "idx_release_jobs_status", columnList = "status"),
                @Index(name = "idx_release_jobs_deleted", columnList = "deleted"),
                @Index(name = "idx_release_jobs_created_at", columnList = "created_at"),
                @Index(name = "idx_release_jobs_retry_of", columnList = "retry_of_job_id")
        }
)
public class ReleaseJob {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "source_release_branch", nullable = false, length = 128)
    private String sourceReleaseBranch;

    @Column(name = "channel_release_branch", nullable = false, length = 128)
    private String channelReleaseBranch;

    @Column(name = "app_version_name", nullable = false, length = 64)
    private String appVersionName;

    @Column(name = "app_version_code", nullable = false)
    private Integer appVersionCode;

    @Column(name = "engine_version_name", nullable = false, length = 64)
    private String engineVersionName;

    @Column(name = "engine_version_code", nullable = false)
    private Integer engineVersionCode;

    @Column(name = "announcement_content", nullable = false, length = 4000)
    private String announcementContent;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private Integer progress = 0;

    @Column(name = "log_excerpt", columnDefinition = "TEXT")
    private String logExcerpt;

    @Column(name = "app_artifact_url", length = 512)
    private String appArtifactUrl;

    @Column(name = "app_artifact_md5", length = 32)
    private String appArtifactMd5;

    @Column(name = "app_artifact_sha256", length = 64)
    private String appArtifactSha256;

    @Column(name = "app_artifact_size")
    private Long appArtifactSize;

    @Column(name = "engine_artifact_url", length = 512)
    private String engineArtifactUrl;

    @Column(name = "engine_artifact_md5", length = 32)
    private String engineArtifactMd5;

    @Column(name = "engine_artifact_sha256", length = 64)
    private String engineArtifactSha256;

    @Column(name = "engine_artifact_size")
    private Long engineArtifactSize;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "retry_of_job_id")
    private Long retryOfJobId;

    @JsonIgnore
    @Column(name = "callback_token_hash", length = 128)
    private String callbackTokenHash;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

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
        if (progress == null) {
            progress = 0;
        }
        if (status == null) {
            status = STATUS_PENDING;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        normalize();
        if (progress == null) {
            progress = 0;
        }
        updatedAt = LocalDateTime.now();
    }

    private void normalize() {
        sourceReleaseBranch = normalize(sourceReleaseBranch);
        channelReleaseBranch = normalize(channelReleaseBranch);
        appVersionName = normalize(appVersionName);
        engineVersionName = normalize(engineVersionName);
        announcementContent = normalize(announcementContent);
        status = normalizeStatus(status);
        logExcerpt = normalize(logExcerpt);
        appArtifactUrl = normalize(appArtifactUrl);
        appArtifactMd5 = normalizeLower(appArtifactMd5);
        appArtifactSha256 = normalizeLower(appArtifactSha256);
        engineArtifactUrl = normalize(engineArtifactUrl);
        engineArtifactMd5 = normalizeLower(engineArtifactMd5);
        engineArtifactSha256 = normalizeLower(engineArtifactSha256);
        callbackTokenHash = normalizeLower(callbackTokenHash);
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return STATUS_PENDING;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case STATUS_RUNNING, STATUS_SUCCESS, STATUS_FAILED, STATUS_CANCELLED -> normalized;
            default -> STATUS_PENDING;
        };
    }

    private String normalizeLower(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getSourceReleaseBranch() { return sourceReleaseBranch; }
    public void setSourceReleaseBranch(String sourceReleaseBranch) { this.sourceReleaseBranch = normalize(sourceReleaseBranch); }

    public String getChannelReleaseBranch() { return channelReleaseBranch; }
    public void setChannelReleaseBranch(String channelReleaseBranch) { this.channelReleaseBranch = normalize(channelReleaseBranch); }

    public String getAppVersionName() { return appVersionName; }
    public void setAppVersionName(String appVersionName) { this.appVersionName = normalize(appVersionName); }

    public Integer getAppVersionCode() { return appVersionCode; }
    public void setAppVersionCode(Integer appVersionCode) { this.appVersionCode = appVersionCode; }

    public String getEngineVersionName() { return engineVersionName; }
    public void setEngineVersionName(String engineVersionName) { this.engineVersionName = normalize(engineVersionName); }

    public Integer getEngineVersionCode() { return engineVersionCode; }
    public void setEngineVersionCode(Integer engineVersionCode) { this.engineVersionCode = engineVersionCode; }

    public String getAnnouncementContent() { return announcementContent; }
    public void setAnnouncementContent(String announcementContent) { this.announcementContent = normalize(announcementContent); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = normalizeStatus(status); }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress == null ? 0 : Math.max(0, Math.min(100, progress)); }

    public String getLogExcerpt() { return logExcerpt; }
    public void setLogExcerpt(String logExcerpt) { this.logExcerpt = normalize(logExcerpt); }

    public String getAppArtifactUrl() { return appArtifactUrl; }
    public void setAppArtifactUrl(String appArtifactUrl) { this.appArtifactUrl = normalize(appArtifactUrl); }

    public String getAppArtifactMd5() { return appArtifactMd5; }
    public void setAppArtifactMd5(String appArtifactMd5) { this.appArtifactMd5 = normalizeLower(appArtifactMd5); }

    public String getAppArtifactSha256() { return appArtifactSha256; }
    public void setAppArtifactSha256(String appArtifactSha256) { this.appArtifactSha256 = normalizeLower(appArtifactSha256); }

    public Long getAppArtifactSize() { return appArtifactSize; }
    public void setAppArtifactSize(Long appArtifactSize) { this.appArtifactSize = appArtifactSize; }

    public String getEngineArtifactUrl() { return engineArtifactUrl; }
    public void setEngineArtifactUrl(String engineArtifactUrl) { this.engineArtifactUrl = normalize(engineArtifactUrl); }

    public String getEngineArtifactMd5() { return engineArtifactMd5; }
    public void setEngineArtifactMd5(String engineArtifactMd5) { this.engineArtifactMd5 = normalizeLower(engineArtifactMd5); }

    public String getEngineArtifactSha256() { return engineArtifactSha256; }
    public void setEngineArtifactSha256(String engineArtifactSha256) { this.engineArtifactSha256 = normalizeLower(engineArtifactSha256); }

    public Long getEngineArtifactSize() { return engineArtifactSize; }
    public void setEngineArtifactSize(Long engineArtifactSize) { this.engineArtifactSize = engineArtifactSize; }

    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }

    public Long getRetryOfJobId() { return retryOfJobId; }
    public void setRetryOfJobId(Long retryOfJobId) { this.retryOfJobId = retryOfJobId; }

    public String getCallbackTokenHash() { return callbackTokenHash; }
    public void setCallbackTokenHash(String callbackTokenHash) { this.callbackTokenHash = normalizeLower(callbackTokenHash); }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
