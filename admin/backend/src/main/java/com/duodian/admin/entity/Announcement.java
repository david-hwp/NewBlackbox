package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "announcements")
public class Announcement {
    private static final String DEFAULT_TYPE = "NORMAL";
    private static final String APP_RELEASE_TYPE = "APP_RELEASE";
    private static final String APP_RELEASE_TITLE = "新版本发布";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32) DEFAULT 'NORMAL'")
    private String type = "NORMAL";

    @Column(nullable = false)
    private Boolean published = false;

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
        normalizeReleaseAnnouncement();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeReleaseAnnouncement();
        updatedAt = LocalDateTime.now();
    }

    private void normalizeReleaseAnnouncement() {
        if (type == null || type.isBlank()) {
            type = DEFAULT_TYPE;
        } else {
            type = type.trim().toUpperCase(Locale.ROOT);
        }
        if (APP_RELEASE_TYPE.equals(type)) {
            title = APP_RELEASE_TITLE;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) {
        this.type = (type == null || type.isBlank()) ? DEFAULT_TYPE : type.trim().toUpperCase(Locale.ROOT);
    }

    public Boolean getPublished() { return published; }
    public void setPublished(Boolean published) { this.published = published; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
