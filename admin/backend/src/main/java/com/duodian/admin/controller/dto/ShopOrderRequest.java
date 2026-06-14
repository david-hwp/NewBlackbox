package com.duodian.admin.controller.dto;

import java.util.List;

public class ShopOrderRequest {
    private List<Long> shopIds;

    public List<Long> getShopIds() {
        return shopIds;
    }

    public void setShopIds(List<Long> shopIds) {
        this.shopIds = shopIds;
    }
}
