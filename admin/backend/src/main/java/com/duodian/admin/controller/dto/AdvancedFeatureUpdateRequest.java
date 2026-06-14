package com.duodian.admin.controller.dto;

import java.util.List;

public class AdvancedFeatureUpdateRequest {
    private Boolean online;
    private Integer monthlyComputeCost;
    private List<String> supportedPlatformPackages;
    private String title;
    private String line1;
    private String line2;
    private Boolean outboundEnabled;

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

    public Boolean getOutboundEnabled() { return outboundEnabled; }
    public void setOutboundEnabled(Boolean outboundEnabled) { this.outboundEnabled = outboundEnabled; }
}
