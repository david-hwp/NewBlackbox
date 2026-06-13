package com.duodian.admin.controller.dto;

public class ReleaseJobProgressRequest {
    private Integer progress;
    private String logExcerpt;

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public String getLogExcerpt() { return logExcerpt; }
    public void setLogExcerpt(String logExcerpt) { this.logExcerpt = logExcerpt; }
}
