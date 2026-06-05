package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ShopReportRequest;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ComputeService;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/shops")
public class ShopReportController {

    private final ShopService shopService;
    private final ComputeService computeService;
    private final UserService userService;

    public ShopReportController(ShopService shopService, ComputeService computeService, UserService userService) {
        this.shopService = shopService;
        this.computeService = computeService;
        this.userService = userService;
    }

    @PostMapping("/report")
    public ApiResponse<Map<String, Object>> report(@RequestBody ShopReportRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        boolean hasRealShopId = isRealShopId(request.getShopId());
        Optional<Shop> existing = Optional.empty();
        String cloneInstanceId = normalize(request.getCloneInstanceId());
        if (cloneInstanceId != null) {
            existing = shopService.findByUserIdAndCloneInstanceId(userId, cloneInstanceId);
        }
        if (existing.isEmpty() && "-".equals(request.getShopId())) {
            existing = shopService.findByUserIdAndPackageNameAndPlatform(
                    userId,
                    request.getPackageName(),
                    request.getPlatform()
            );
        } else if (existing.isEmpty()) {
            existing = shopService.findByUserIdAndShopId(userId, request.getShopId());
        }
        if (existing.isEmpty() && hasRealShopId) {
            existing = shopService.findPendingByUserPackageAndPlatform(
                    userId,
                    request.getPackageName(),
                    request.getPlatform()
            );
        }
        if (existing.isEmpty() && !hasRealShopId) {
            return ApiResponse.error("待登录店铺不存在，请先添加店铺卡片");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("shopId", request.getShopId());
        result.put("cloneInstanceId", cloneInstanceId);

        if (existing.isPresent()) {
            Shop shop = existing.get();
            boolean wasPending = shop.getShopId() != null && shop.getShopId().startsWith("NEW-");

            if (wasPending && hasRealShopId) {
                boolean deducted = computeService.deductCompute(userId, request.getShopId(), request.getShopName(), request.getPlatform());
                if (!deducted) {
                    return ApiResponse.error(402, "算力余额不足");
                }
                shop.setShopId(request.getShopId());
                shop.setExpireAt(LocalDateTime.now().plusDays(30));
                shop.setLastDeductedAt(LocalDateTime.now());
                result.put("deducted", true);
                result.put("isNew", true);
            } else {
                result.put("deducted", false);
                result.put("isNew", false);
            }
            shop.setShopName(request.getShopName());
            shop.setPlatform(request.getPlatform());
            shop.setPlatformName(request.getPlatformName());
            shop.setPackageName(request.getPackageName());
            shop.setRemainingDays(request.getRemainingDays());
            shop.setAutoRenew(request.getAutoRenew());
            if (cloneInstanceId != null) {
                shop.setCloneInstanceId(cloneInstanceId);
            }
            shopService.update(shop.getId(), shop);
        } else {
            // New shop: deduct compute
            boolean deducted = computeService.deductCompute(userId, request.getShopId(), request.getShopName(), request.getPlatform());
            if (!deducted) {
                return ApiResponse.error(402, "算力余额不足");
            }

            Shop shop = new Shop();
            shop.setUserId(userId);
            shop.setShopName(request.getShopName());
            shop.setShopId(request.getShopId());
            shop.setPlatform(request.getPlatform());
            shop.setPlatformName(request.getPlatformName());
            shop.setPackageName(request.getPackageName());
            shop.setCloneInstanceId(cloneInstanceId);
            shop.setRemainingDays(request.getRemainingDays());
            shop.setAutoRenew(request.getAutoRenew());
            shop.setExpireAt(LocalDateTime.now().plusDays(30));
            shop.setLastDeductedAt(LocalDateTime.now());
            shopService.create(shop);

            result.put("deducted", true);
            result.put("isNew", true);
        }

        User user = userService.refreshShopStats(userId);
        result.put("balance", user != null ? user.getComputeBalance() : 0);
        result.put("shopCount", user != null ? user.getShopCount() : 0);
        result.put("platformCount", user != null ? user.getPlatformCount() : 0);

        return ApiResponse.success(result);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isRealShopId(String shopId) {
        return shopId != null
                && !shopId.isBlank()
                && !"-".equals(shopId)
                && !shopId.startsWith("NEW-");
    }
}
