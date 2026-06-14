package com.duodian.admin.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AdvancedFeatureResponse {
    private Long id;
    private String code;
    private String name;
    private Boolean online;
    private Integer monthlyComputeCost;
    private List<String> supportedPlatformPackages;
    private String title;
    private String line1;
    private String line2;
    private String titleCode;
    private String line1Code;
    private String line2Code;
    private Boolean outboundEnabled;
    private Integer sortOrder;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }

    public Integer getMonthlyComputeCost() { return monthlyComputeCost; }
    public void setMonthlyComputeCost(Integer monthlyComputeCost) { this.monthlyComputeCost = monthlyComputeCost; }

    public List<String> getSupportedPlatformPackages() { return supportedPlatformPackages; }
    public void setSupportedPlatformPackages(List<String> supportedPlatformPackages) {
        this.supportedPlatformPackages = supportedPlatformPackages;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }

    public String getLine2() { return line2; }
    public void setLine2(String line2) { this.line2 = line2; }

    public String getTitleCode() { return titleCode; }
    public void setTitleCode(String titleCode) { this.titleCode = titleCode; }

    public String getLine1Code() { return line1Code; }
    public void setLine1Code(String line1Code) { this.line1Code = line1Code; }

    public String getLine2Code() { return line2Code; }
    public void setLine2Code(String line2Code) { this.line2Code = line2Code; }

    public Boolean getOutboundEnabled() { return outboundEnabled; }
    public void setOutboundEnabled(Boolean outboundEnabled) { this.outboundEnabled = outboundEnabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
