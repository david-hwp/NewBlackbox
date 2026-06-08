package com.duodian.admin.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ShopReportRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "店铺名称不能为空")
    private String shopName;

    @NotBlank(message = "店铺ID不能为空")
    private String shopId;

    @NotBlank(message = "平台不能为空")
    private String platform;

    private String platformName;

    @NotBlank(message = "包名不能为空")
    private String packageName;

    private Integer remainingDays = 30;

    private Boolean autoRenew = false;

    private String cloneInstanceId;

    private Integer localVirtualUserId;

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public Integer getRemainingDays() { return remainingDays; }
    public void setRemainingDays(Integer remainingDays) { this.remainingDays = remainingDays; }

    public Boolean getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }

    public String getCloneInstanceId() { return cloneInstanceId; }
    public void setCloneInstanceId(String cloneInstanceId) { this.cloneInstanceId = cloneInstanceId; }

    public Integer getLocalVirtualUserId() { return localVirtualUserId; }
    public void setLocalVirtualUserId(Integer localVirtualUserId) { this.localVirtualUserId = localVirtualUserId; }
}
