package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;

import java.time.LocalDateTime;

public class CloneShopCreateResponse {
    private Shop shop;
    private Integer balance;
    private Integer shopCount;
    private Integer platformCount;
    private Boolean deducted;
    private String authorizationToken;
    private String publicKeyId;
    private LocalDateTime authStartAt;
    private LocalDateTime authExpireAt;

    public CloneShopCreateResponse() {
    }

    public static CloneShopCreateResponse from(
            Shop shop,
            User user,
            boolean deducted,
            String authorizationToken
    ) {
        return from(shop, user, deducted, authorizationToken, null);
    }

    public static CloneShopCreateResponse from(
            Shop shop,
            User user,
            boolean deducted,
            String authorizationToken,
            String publicKeyId
    ) {
        CloneShopCreateResponse response = new CloneShopCreateResponse();
        response.setShop(shop);
        response.setBalance(user != null ? user.getComputeBalance() : 0);
        response.setShopCount(user != null ? user.getShopCount() : 0);
        response.setPlatformCount(user != null ? user.getPlatformCount() : 0);
        response.setDeducted(deducted);
        response.setAuthorizationToken(authorizationToken);
        response.setPublicKeyId(publicKeyId);
        response.setAuthStartAt(shop.getAuthStartAt());
        response.setAuthExpireAt(shop.getAuthExpireAt());
        return response;
    }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }

    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }

    public Integer getShopCount() { return shopCount; }
    public void setShopCount(Integer shopCount) { this.shopCount = shopCount; }

    public Integer getPlatformCount() { return platformCount; }
    public void setPlatformCount(Integer platformCount) { this.platformCount = platformCount; }

    public Boolean getDeducted() { return deducted; }
    public void setDeducted(Boolean deducted) { this.deducted = deducted; }

    public String getAuthorizationToken() { return authorizationToken; }
    public void setAuthorizationToken(String authorizationToken) { this.authorizationToken = authorizationToken; }

    public String getPublicKeyId() { return publicKeyId; }
    public void setPublicKeyId(String publicKeyId) { this.publicKeyId = publicKeyId; }

    public LocalDateTime getAuthStartAt() { return authStartAt; }
    public void setAuthStartAt(LocalDateTime authStartAt) { this.authStartAt = authStartAt; }

    public LocalDateTime getAuthExpireAt() { return authExpireAt; }
    public void setAuthExpireAt(LocalDateTime authExpireAt) { this.authExpireAt = authExpireAt; }
}
