package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;

public class ShopRenewResponse {
    private Shop shop;
    private Integer balance;
    private Integer shopCount;
    private Integer platformCount;

    public ShopRenewResponse() {
    }

    public static ShopRenewResponse from(Shop shop, User user) {
        ShopRenewResponse response = new ShopRenewResponse();
        response.setShop(shop);
        response.setBalance(user != null ? user.getComputeBalance() : 0);
        response.setShopCount(user != null ? user.getShopCount() : 0);
        response.setPlatformCount(user != null ? user.getPlatformCount() : 0);
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
}
