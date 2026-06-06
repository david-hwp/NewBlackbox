package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_phone")
    private String userPhone;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "image_urls", length = 1000)
    private String imageUrls; // 逗号分隔的图片URL

    @Column(name = "attachment_urls", length = 1000)
    private String attachmentUrls;

    @Column(name = "log_url")
    private String logUrl; // 日志文件URL

    @Column(length = 32)
    private String source = "APP"; // APP / ENGINE_LOG

    @Column(name = "log_caption", length = 1000)
    private String logCaption;

    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;

    @Column(length = 20)
    private String status = "PENDING"; // PENDING / PROCESSING / RESOLVED

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (deleted == null) {
            deleted = 0;
        }
        createdAt = LocalDateTime.now();
    }

    public Feedback() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }

    public String getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(String attachmentUrls) { this.attachmentUrls = attachmentUrls; }

    public String getLogUrl() { return logUrl; }
    public void setLogUrl(String logUrl) { this.logUrl = logUrl; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getLogCaption() { return logCaption; }
    public void setLogCaption(String logCaption) { this.logCaption = logCaption; }

    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
