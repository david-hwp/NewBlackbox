package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.ReleaseJob;

import java.time.LocalDateTime;

public class ReleaseJobResponse {
    private Long id;
    private Long channelId;
    private String channelCode;
    private String channelName;
    private String sourceReleaseBranch;
    private String channelReleaseBranch;
    private String appVersionName;
    private Integer appVersionCode;
    private String engineVersionName;
    private Integer engineVersionCode;
    private String announcementContent;
    private String status;
    private Integer progress;
    private String logExcerpt;
    private String appArtifactUrl;
    private String appArtifactMd5;
    private String appArtifactSha256;
    private Long appArtifactSize;
    private String engineArtifactUrl;
    private String engineArtifactMd5;
    private String engineArtifactSha256;
    private Long engineArtifactSize;
    private Long requestedBy;
    private Long retryOfJobId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReleaseJobResponse from(ReleaseJob job, Channel channel) {
        ReleaseJobResponse response = new ReleaseJobResponse();
        response.setId(job.getId());
        response.setChannelId(job.getChannelId());
        if (channel != null) {
            response.setChannelCode(channel.getCode());
            response.setChannelName(channel.getName());
        }
        response.setSourceReleaseBranch(job.getSourceReleaseBranch());
        response.setChannelReleaseBranch(job.getChannelReleaseBranch());
        response.setAppVersionName(job.getAppVersionName());
        response.setAppVersionCode(job.getAppVersionCode());
        response.setEngineVersionName(job.getEngineVersionName());
        response.setEngineVersionCode(job.getEngineVersionCode());
        response.setAnnouncementContent(job.getAnnouncementContent());
        response.setStatus(job.getStatus());
        response.setProgress(job.getProgress());
        response.setLogExcerpt(job.getLogExcerpt());
        response.setAppArtifactUrl(job.getAppArtifactUrl());
        response.setAppArtifactMd5(job.getAppArtifactMd5());
        response.setAppArtifactSha256(job.getAppArtifactSha256());
        response.setAppArtifactSize(job.getAppArtifactSize());
        response.setEngineArtifactUrl(job.getEngineArtifactUrl());
        response.setEngineArtifactMd5(job.getEngineArtifactMd5());
        response.setEngineArtifactSha256(job.getEngineArtifactSha256());
        response.setEngineArtifactSize(job.getEngineArtifactSize());
        response.setRequestedBy(job.getRequestedBy());
        response.setRetryOfJobId(job.getRetryOfJobId());
        response.setStartedAt(job.getStartedAt());
        response.setFinishedAt(job.getFinishedAt());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public String getSourceReleaseBranch() { return sourceReleaseBranch; }
    public void setSourceReleaseBranch(String sourceReleaseBranch) { this.sourceReleaseBranch = sourceReleaseBranch; }

    public String getChannelReleaseBranch() { return channelReleaseBranch; }
    public void setChannelReleaseBranch(String channelReleaseBranch) { this.channelReleaseBranch = channelReleaseBranch; }

    public String getAppVersionName() { return appVersionName; }
    public void setAppVersionName(String appVersionName) { this.appVersionName = appVersionName; }

    public Integer getAppVersionCode() { return appVersionCode; }
    public void setAppVersionCode(Integer appVersionCode) { this.appVersionCode = appVersionCode; }

    public String getEngineVersionName() { return engineVersionName; }
    public void setEngineVersionName(String engineVersionName) { this.engineVersionName = engineVersionName; }

    public Integer getEngineVersionCode() { return engineVersionCode; }
    public void setEngineVersionCode(Integer engineVersionCode) { this.engineVersionCode = engineVersionCode; }

    public String getAnnouncementContent() { return announcementContent; }
    public void setAnnouncementContent(String announcementContent) { this.announcementContent = announcementContent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public String getLogExcerpt() { return logExcerpt; }
    public void setLogExcerpt(String logExcerpt) { this.logExcerpt = logExcerpt; }

    public String getAppArtifactUrl() { return appArtifactUrl; }
    public void setAppArtifactUrl(String appArtifactUrl) { this.appArtifactUrl = appArtifactUrl; }

    public String getAppArtifactMd5() { return appArtifactMd5; }
    public void setAppArtifactMd5(String appArtifactMd5) { this.appArtifactMd5 = appArtifactMd5; }

    public String getAppArtifactSha256() { return appArtifactSha256; }
    public void setAppArtifactSha256(String appArtifactSha256) { this.appArtifactSha256 = appArtifactSha256; }

    public Long getAppArtifactSize() { return appArtifactSize; }
    public void setAppArtifactSize(Long appArtifactSize) { this.appArtifactSize = appArtifactSize; }

    public String getEngineArtifactUrl() { return engineArtifactUrl; }
    public void setEngineArtifactUrl(String engineArtifactUrl) { this.engineArtifactUrl = engineArtifactUrl; }

    public String getEngineArtifactMd5() { return engineArtifactMd5; }
    public void setEngineArtifactMd5(String engineArtifactMd5) { this.engineArtifactMd5 = engineArtifactMd5; }

    public String getEngineArtifactSha256() { return engineArtifactSha256; }
    public void setEngineArtifactSha256(String engineArtifactSha256) { this.engineArtifactSha256 = engineArtifactSha256; }

    public Long getEngineArtifactSize() { return engineArtifactSize; }
    public void setEngineArtifactSize(Long engineArtifactSize) { this.engineArtifactSize = engineArtifactSize; }

    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }

    public Long getRetryOfJobId() { return retryOfJobId; }
    public void setRetryOfJobId(Long retryOfJobId) { this.retryOfJobId = retryOfJobId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
