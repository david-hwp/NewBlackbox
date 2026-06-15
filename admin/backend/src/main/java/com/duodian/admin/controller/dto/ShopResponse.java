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
    private Boolean identityVerified;
    private LocalDateTime identityVerifiedAt;
    private String platform;
    private String platformName;
    private Integer cardSortOrder;
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
    private Boolean hasLoginState;
    private String loginStateProfile;
    private Long loginStateSize;
    private String loginStateSha256;
    private LocalDateTime loginStateUpdatedAt;
    private LocalDateTime loginStateArtifactCreatedAt;
    private String shopAuthorizationStatus;
    private LocalDateTime shopAuthorizationCheckedAt;
    private String shopAuthorizationSignals;
    private String shopAuthorizationUrl;
    private String wechatReceiverId;
    private String wechatReceiverName;
    private String wechatReceiverType;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShopResponse from(Shop shop, User user) {
        return from(shop, user, false);
    }

    public static ShopResponse from(Shop shop, User user, boolean includeShopAuthorizationUrl) {
        ShopResponse response = new ShopResponse();
        response.setId(shop.getId());
        response.setUserId(shop.getUserId());
        response.setUserName(user != null ? user.getUsername() : null);
        response.setUserPhone(user != null ? user.getPhone() : null);
        response.setChannelId(shop.getChannelId());
        response.setShopName(shop.getShopName());
        response.setShopId(shop.getShopId());
        response.setIdentityVerified(Boolean.TRUE.equals(shop.getIdentityVerified()));
        response.setIdentityVerifiedAt(shop.getIdentityVerifiedAt());
        response.setPlatform(shop.getPlatform());
        response.setPlatformName(shop.getPlatformName());
        response.setCardSortOrder(shop.getCardSortOrder() == null ? 0 : shop.getCardSortOrder());
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
        response.setHasLoginState(shop.getLoginStateSize() != null && shop.getLoginStateSize() > 0);
        response.setLoginStateProfile(shop.getLoginStateProfile());
        response.setLoginStateSize(shop.getLoginStateSize());
        response.setLoginStateSha256(shop.getLoginStateSha256());
        response.setLoginStateUpdatedAt(shop.getLoginStateUpdatedAt());
        response.setLoginStateArtifactCreatedAt(shop.getLoginStateArtifactCreatedAt());
        response.setShopAuthorizationStatus(shop.getShopAuthorizationStatus());
        response.setShopAuthorizationCheckedAt(shop.getShopAuthorizationCheckedAt());
        response.setShopAuthorizationSignals(shop.getShopAuthorizationSignals());
        response.setShopAuthorizationUrl(includeShopAuthorizationUrl ? shop.getShopAuthorizationUrl() : null);
        response.setWechatReceiverId(shop.getWechatReceiverId());
        response.setWechatReceiverName(shop.getWechatReceiverName());
        response.setWechatReceiverType(shop.getWechatReceiverType());
        response.setRemark(shop.getRemark());
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

    public Boolean getIdentityVerified() { return identityVerified; }
    public void setIdentityVerified(Boolean identityVerified) { this.identityVerified = identityVerified; }

    public LocalDateTime getIdentityVerifiedAt() { return identityVerifiedAt; }
    public void setIdentityVerifiedAt(LocalDateTime identityVerifiedAt) { this.identityVerifiedAt = identityVerifiedAt; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }

    public Integer getCardSortOrder() { return cardSortOrder; }
    public void setCardSortOrder(Integer cardSortOrder) { this.cardSortOrder = cardSortOrder; }

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

    public Boolean getHasLoginState() { return hasLoginState; }
    public void setHasLoginState(Boolean hasLoginState) { this.hasLoginState = hasLoginState; }

    public String getLoginStateProfile() { return loginStateProfile; }
    public void setLoginStateProfile(String loginStateProfile) { this.loginStateProfile = loginStateProfile; }

    public Long getLoginStateSize() { return loginStateSize; }
    public void setLoginStateSize(Long loginStateSize) { this.loginStateSize = loginStateSize; }

    public String getLoginStateSha256() { return loginStateSha256; }
    public void setLoginStateSha256(String loginStateSha256) { this.loginStateSha256 = loginStateSha256; }

    public LocalDateTime getLoginStateUpdatedAt() { return loginStateUpdatedAt; }
    public void setLoginStateUpdatedAt(LocalDateTime loginStateUpdatedAt) { this.loginStateUpdatedAt = loginStateUpdatedAt; }

    public LocalDateTime getLoginStateArtifactCreatedAt() { return loginStateArtifactCreatedAt; }
    public void setLoginStateArtifactCreatedAt(LocalDateTime loginStateArtifactCreatedAt) { this.loginStateArtifactCreatedAt = loginStateArtifactCreatedAt; }

    public String getShopAuthorizationStatus() { return shopAuthorizationStatus; }
    public void setShopAuthorizationStatus(String shopAuthorizationStatus) { this.shopAuthorizationStatus = shopAuthorizationStatus; }

    public LocalDateTime getShopAuthorizationCheckedAt() { return shopAuthorizationCheckedAt; }
    public void setShopAuthorizationCheckedAt(LocalDateTime shopAuthorizationCheckedAt) { this.shopAuthorizationCheckedAt = shopAuthorizationCheckedAt; }

    public String getShopAuthorizationSignals() { return shopAuthorizationSignals; }
    public void setShopAuthorizationSignals(String shopAuthorizationSignals) { this.shopAuthorizationSignals = shopAuthorizationSignals; }

    public String getShopAuthorizationUrl() { return shopAuthorizationUrl; }
    public void setShopAuthorizationUrl(String shopAuthorizationUrl) { this.shopAuthorizationUrl = shopAuthorizationUrl; }

    public String getWechatReceiverId() { return wechatReceiverId; }
    public void setWechatReceiverId(String wechatReceiverId) { this.wechatReceiverId = wechatReceiverId; }

    public String getWechatReceiverName() { return wechatReceiverName; }
    public void setWechatReceiverName(String wechatReceiverName) { this.wechatReceiverName = wechatReceiverName; }

    public String getWechatReceiverType() { return wechatReceiverType; }
    public void setWechatReceiverType(String wechatReceiverType) { this.wechatReceiverType = wechatReceiverType; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
