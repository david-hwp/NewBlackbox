package com.duodian.admin.controller.dto;

public class ReleaseJobCompleteRequest {
    private Boolean success;
    private String status;
    private Integer progress;
    private String logExcerpt;
    private String errorMessage;
    private String appArtifactUrl;
    private String appArtifactMd5;
    private String appArtifactSha256;
    private Long appArtifactSize;
    private String engineArtifactUrl;
    private String engineArtifactMd5;
    private String engineArtifactSha256;
    private Long engineArtifactSize;

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public String getLogExcerpt() { return logExcerpt; }
    public void setLogExcerpt(String logExcerpt) { this.logExcerpt = logExcerpt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

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
}
