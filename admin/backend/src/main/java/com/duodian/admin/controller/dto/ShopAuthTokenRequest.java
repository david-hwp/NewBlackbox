package com.duodian.admin.controller.dto;

public class ShopAuthTokenRequest {
    private Integer localVirtualUserId;
    private String packageName;

    public Integer getLocalVirtualUserId() { return localVirtualUserId; }
    public void setLocalVirtualUserId(Integer localVirtualUserId) { this.localVirtualUserId = localVirtualUserId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
}
