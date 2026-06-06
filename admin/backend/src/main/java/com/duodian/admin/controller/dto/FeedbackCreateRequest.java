package com.duodian.admin.controller.dto;

import java.util.ArrayList;
import java.util.List;

public class FeedbackCreateRequest {
    private String content;
    private List<String> imageUrls = new ArrayList<>();
    private List<String> attachmentUrls = new ArrayList<>();
    private String logUrl;
    private String source;
    private String logCaption;
    private String deviceInfo;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public List<String> getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(List<String> attachmentUrls) { this.attachmentUrls = attachmentUrls; }

    public String getLogUrl() { return logUrl; }
    public void setLogUrl(String logUrl) { this.logUrl = logUrl; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getLogCaption() { return logCaption; }
    public void setLogCaption(String logCaption) { this.logCaption = logCaption; }

    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
}
