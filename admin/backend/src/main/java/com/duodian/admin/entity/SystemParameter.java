package com.duodian.admin.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "system_parameters",
        indexes = {
                @Index(name = "idx_system_parameters_channel_id", columnList = "channel_id"),
                @Index(name = "idx_system_parameters_code", columnList = "code"),
                @Index(name = "idx_system_parameters_deleted", columnList = "deleted")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_parameters_channel_code_deleted", columnNames = {"channel_id", "code", "deleted"})
        }
)
public class SystemParameter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(name = "param_value", nullable = false, length = 1024)
    private String value;

    @Column(length = 512)
    private String description;

    @Column(name = "is_builtin", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte builtin = 0;

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
        if (builtin == null) {
            builtin = 0;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        normalize();
        updatedAt = LocalDateTime.now();
    }

    private void normalize() {
        name = normalizeRequired(name, code);
        code = normalizeRequired(code, null);
        value = normalizeRequired(value, "");
        description = normalize(description);
    }

    private String normalizeRequired(String value, String fallback) {
        String normalized = normalize(value);
        if (normalized != null) {
            return normalized;
        }
        return fallback == null ? "" : fallback;
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Byte getBuiltin() { return builtin; }
    public void setBuiltin(Byte builtin) { this.builtin = builtin == null ? 0 : builtin; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
