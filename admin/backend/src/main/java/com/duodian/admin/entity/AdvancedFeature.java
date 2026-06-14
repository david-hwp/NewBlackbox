package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "advanced_features",
        indexes = {
                @Index(name = "idx_advanced_features_code", columnList = "code"),
                @Index(name = "idx_advanced_features_deleted", columnList = "deleted")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_advanced_features_code_deleted", columnNames = {"code", "deleted"})
        }
)
public class AdvancedFeature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "is_online", nullable = false)
    private Boolean online = true;

    @Column(name = "monthly_compute_cost", nullable = false)
    private Integer monthlyComputeCost = 1;

    @Column(name = "supported_platform_packages", length = 2048)
    private String supportedPlatformPackages;

    @Column(name = "title_code", nullable = false, length = 128)
    private String titleCode;

    @Column(name = "line1_code", nullable = false, length = 128)
    private String line1Code;

    @Column(name = "line2_code", nullable = false, length = 128)
    private String line2Code;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "line1_text", nullable = false, length = 255)
    private String line1;

    @Column(name = "line2_text", nullable = false, length = 255)
    private String line2;

    @Column(name = "outbound_enabled", nullable = false)
    private Boolean outboundEnabled = false;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

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
        if (online == null) {
            online = true;
        }
        if (outboundEnabled == null) {
            outboundEnabled = false;
        }
        if (monthlyComputeCost == null) {
            monthlyComputeCost = 1;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (title == null) {
            title = name;
        }
        if (line1 == null) {
            line1 = "-";
        }
        if (line2 == null) {
            line2 = "-";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = normalize(code); }

    public String getName() { return name; }
    public void setName(String name) { this.name = normalize(name); }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = Boolean.TRUE.equals(online); }

    public Integer getMonthlyComputeCost() { return monthlyComputeCost; }
    public void setMonthlyComputeCost(Integer monthlyComputeCost) {
        this.monthlyComputeCost = monthlyComputeCost == null ? 0 : Math.max(0, monthlyComputeCost);
    }

    public String getSupportedPlatformPackages() { return supportedPlatformPackages; }
    public void setSupportedPlatformPackages(String supportedPlatformPackages) {
        this.supportedPlatformPackages = normalize(supportedPlatformPackages);
    }

    public String getTitleCode() { return titleCode; }
    public void setTitleCode(String titleCode) { this.titleCode = normalize(titleCode); }

    public String getLine1Code() { return line1Code; }
    public void setLine1Code(String line1Code) { this.line1Code = normalize(line1Code); }

    public String getLine2Code() { return line2Code; }
    public void setLine2Code(String line2Code) { this.line2Code = normalize(line2Code); }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = normalize(title); }

    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = normalize(line1); }

    public String getLine2() { return line2; }
    public void setLine2(String line2) { this.line2 = normalize(line2); }

    public Boolean getOutboundEnabled() { return outboundEnabled; }
    public void setOutboundEnabled(Boolean outboundEnabled) { this.outboundEnabled = Boolean.TRUE.equals(outboundEnabled); }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder == null ? 0 : sortOrder; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
