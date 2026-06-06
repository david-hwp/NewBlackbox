package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.service.ShopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/shops")
public class ShopValidationController {

    private final ShopService shopService;

    public ShopValidationController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/validate")
    public ApiResponse<List<Map<String, Object>>> validate(
            @RequestParam("shopIds") String shopIds,
            @RequestParam(required = false) String packageName) {

        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        if (shopIds == null || shopIds.isBlank()) {
            return ApiResponse.error("shopIds不能为空");
        }

        String[] ids = shopIds.split(",");
        if (ids.length > 50) {
            return ApiResponse.error("最多校验50个店铺");
        }

        String normalizedPackageName = normalize(packageName);
        List<Map<String, Object>> results = new ArrayList<>();
        for (String shopId : ids) {
            String trimmed = shopId.trim();
            if (trimmed.isEmpty()) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("shopId", trimmed);

            Optional<Shop> shopOpt = normalizedPackageName == null
                    ? shopService.findByUserIdAndShopId(userId, trimmed)
                    : shopService.findByUserIdAndShopIdAndPackageName(userId, trimmed, normalizedPackageName);
            if (shopOpt.isPresent()) {
                Shop shop = shopOpt.get();
                LocalDateTime expireAt = shop.getExpireAt();
                boolean isValid = expireAt != null && expireAt.isAfter(LocalDateTime.now());
                item.put("isValid", isValid);
                item.put("expireAt", expireAt != null ? expireAt.toString() : null);
                item.put("autoRenew", shop.getAutoRenew());
            } else {
                item.put("isValid", false);
                item.put("expireAt", null);
                item.put("autoRenew", false);
            }
            results.add(item);
        }

        return ApiResponse.success(results);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
