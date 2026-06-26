package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;

public class AuthorizedShopOrderCrawlTarget {
    private Long systemShopId;
    private String userPhone;
    private String controlShopId;
    private String shopName;
    private String platform;
    private String platformName;

    public static AuthorizedShopOrderCrawlTarget from(Shop shop, User user) {
        AuthorizedShopOrderCrawlTarget target = new AuthorizedShopOrderCrawlTarget();
        target.setSystemShopId(shop.getId());
        target.setUserPhone(user == null ? null : user.getPhone());
        target.setControlShopId(controlShopId(shop));
        target.setShopName(shop.getShopName());
        target.setPlatform(shop.getPlatform());
        target.setPlatformName(shop.getPlatformName());
        return target;
    }

    private static String controlShopId(Shop shop) {
        String shopId = normalize(shop.getShopId());
        if (shopId != null
                && !"-".equals(shopId)
                && !shopId.startsWith("NEW-")
                && !shopId.startsWith("phase13-")) {
            return shopId;
        }
        return "system-" + shop.getId();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getSystemShopId() { return systemShopId; }
    public void setSystemShopId(Long systemShopId) { this.systemShopId = systemShopId; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public String getControlShopId() { return controlShopId; }
    public void setControlShopId(String controlShopId) { this.controlShopId = controlShopId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }
}
