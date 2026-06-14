package com.duodian.admin.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReleaseJobCreateRequest {
    private Long channelId;

    @NotBlank(message = "源发布分支不能为空")
    @Size(max = 128, message = "源发布分支不能超过128字符")
    private String sourceReleaseBranch;

    @NotBlank(message = "渠道发布分支不能为空")
    @Size(max = 128, message = "渠道发布分支不能超过128字符")
    private String channelReleaseBranch;

    @NotBlank(message = "主APK版本名称不能为空")
    @Size(max = 64, message = "主APK版本名称不能超过64字符")
    private String appVersionName;

    @NotNull(message = "主APK版本号不能为空")
    @Min(value = 1, message = "主APK版本号必须大于0")
    private Integer appVersionCode;

    @NotBlank(message = "引擎版本名称不能为空")
    @Size(max = 64, message = "引擎版本名称不能超过64字符")
    private String engineVersionName;

    @NotNull(message = "引擎版本号不能为空")
    @Min(value = 1, message = "引擎版本号必须大于0")
    private Integer engineVersionCode;

    @NotBlank(message = "发布公告内容不能为空")
    @Size(max = 4000, message = "发布公告内容不能超过4000字符")
    private String announcementContent;

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

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
}
