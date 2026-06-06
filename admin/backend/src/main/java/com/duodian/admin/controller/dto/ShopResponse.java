package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;

import java.time.LocalDateTime;

public class ShopResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userPhone;
    private String shopName;
    private String shopId;
    private String platform;
    private String platformName;
    private Integer remainingDays;
    private Boolean autoRenew;
    private String packageName;
    private String cloneInstanceId;
    private LocalDateTime lastDeductedAt;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShopResponse from(Shop shop, User user) {
        ShopResponse response = new ShopResponse();
        response.setId(shop.getId());
        response.setUserId(shop.getUserId());
        response.setUserName(user != null ? user.getUsername() : null);
        response.setUserPhone(user != null ? user.getPhone() : null);
        response.setShopName(shop.getShopName());
        response.setShopId(shop.getShopId());
        response.setPlatform(shop.getPlatform());
        response.setPlatformName(shop.getPlatformName());
        response.setRemainingDays(shop.getRemainingDays());
        response.setAutoRenew(shop.getAutoRenew());
        response.setPackageName(shop.getPackageName());
        response.setCloneInstanceId(shop.getCloneInstanceId());
        response.setLastDeductedAt(shop.getLastDeductedAt());
        response.setExpireAt(shop.getExpireAt());
        response.setCreatedAt(shop.getCreatedAt());
        response.setUpdatedAt(shop.getUpdatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public Integer getRemainingDays() { return remainingDays; }
    public void setRemainingDays(Integer remainingDays) { this.remainingDays = remainingDays; }

    public Boolean getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getCloneInstanceId() { return cloneInstanceId; }
    public void setCloneInstanceId(String cloneInstanceId) { this.cloneInstanceId = cloneInstanceId; }

    public LocalDateTime getLastDeductedAt() { return lastDeductedAt; }
    public void setLastDeductedAt(LocalDateTime lastDeductedAt) { this.lastDeductedAt = lastDeductedAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
