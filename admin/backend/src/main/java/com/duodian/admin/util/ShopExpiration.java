package com.duodian.admin.util;

import com.duodian.admin.entity.Shop;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class ShopExpiration {
    private ShopExpiration() {
    }

    public static LocalDateTime effectiveExpireAt(Shop shop) {
        if (shop == null) {
            return null;
        }
        return shop.getAuthExpireAt() != null ? shop.getAuthExpireAt() : shop.getExpireAt();
    }

    public static int remainingDays(Shop shop) {
        if (shop == null) {
            return 0;
        }
        LocalDateTime expireAt = effectiveExpireAt(shop);
        if (expireAt == null) {
            return shop.getRemainingDays() == null ? 0 : Math.max(0, shop.getRemainingDays());
        }
        return remainingDays(expireAt, LocalDateTime.now());
    }

    public static int remainingDays(LocalDateTime expireAt, LocalDateTime now) {
        if (expireAt == null || now == null || !expireAt.isAfter(now)) {
            return 0;
        }
        LocalDate today = now.toLocalDate();
        long days = ChronoUnit.DAYS.between(today, expireAt.toLocalDate());
        return (int) Math.max(1, days);
    }

    public static boolean applyRemainingDays(Shop shop) {
        if (shop == null) {
            return false;
        }
        LocalDateTime expireAt = effectiveExpireAt(shop);
        if (expireAt == null) {
            return false;
        }
        int calculated = remainingDays(expireAt, LocalDateTime.now());
        Integer current = shop.getRemainingDays();
        if (current != null && current == calculated) {
            return false;
        }
        shop.setRemainingDays(calculated);
        return true;
    }
}
