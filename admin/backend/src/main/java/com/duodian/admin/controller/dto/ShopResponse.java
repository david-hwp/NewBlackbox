package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.util.ShopExpiration;

import java.time.LocalDateTime;

public class ShopResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userPhone;
    private Long channelId;
    private String channelCode;
    private String channelName;
    private String shopName;
    private String shopId;
    private String platform;
    private String platformName;
    private Integer remainingDays;
    private Boolean autoRenew;
    private String packageName;
    private String cloneInstanceId;
    private Integer cloneSequence;
    private Integer localVirtualUserId;
    private Integer credentialVersion;
    private LocalDateTime authStartAt;
    private LocalDateTime authExpireAt;
    private String authorizationJti;
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
        response.setChannelId(shop.getChannelId());
        response.setShopName(shop.getShopName());
        response.setShopId(shop.getShopId());
        response.setPlatform(shop.getPlatform());
        response.setPlatformName(shop.getPlatformName());
        response.setRemainingDays(ShopExpiration.remainingDays(shop));
        response.setAutoRenew(shop.getAutoRenew());
        response.setPackageName(shop.getPackageName());
        response.setCloneInstanceId(shop.getCloneInstanceId());
        response.setCloneSequence(shop.getCloneSequence());
        response.setLocalVirtualUserId(shop.getLocalVirtualUserId());
        response.setCredentialVersion(shop.getCredentialVersion());
        response.setAuthStartAt(shop.getAuthStartAt());
        response.setAuthExpireAt(shop.getAuthExpireAt());
        response.setAuthorizationJti(shop.getAuthorizationJti());
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

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

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

    public Integer getCloneSequence() { return cloneSequence; }
    public void setCloneSequence(Integer cloneSequence) { this.cloneSequence = cloneSequence; }

    public Integer getLocalVirtualUserId() { return localVirtualUserId; }
    public void setLocalVirtualUserId(Integer localVirtualUserId) { this.localVirtualUserId = localVirtualUserId; }

    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer credentialVersion) { this.credentialVersion = credentialVersion; }

    public LocalDateTime getAuthStartAt() { return authStartAt; }
    public void setAuthStartAt(LocalDateTime authStartAt) { this.authStartAt = authStartAt; }

    public LocalDateTime getAuthExpireAt() { return authExpireAt; }
    public void setAuthExpireAt(LocalDateTime authExpireAt) { this.authExpireAt = authExpireAt; }

    public String getAuthorizationJti() { return authorizationJti; }
    public void setAuthorizationJti(String authorizationJti) { this.authorizationJti = authorizationJti; }

    public LocalDateTime getLastDeductedAt() { return lastDeductedAt; }
    public void setLastDeductedAt(LocalDateTime lastDeductedAt) { this.lastDeductedAt = lastDeductedAt; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
