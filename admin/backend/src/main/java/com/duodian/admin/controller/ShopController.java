package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ShopResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shops")
public class ShopController {

    private final ShopService shopService;
    private final UserService userService;

    public ShopController(ShopService shopService, UserService userService) {
        this.shopService = shopService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<ShopResponse>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String platform) {
        List<Shop> shops;
        if (userId != null) {
            shops = shopService.findByUserId(userId);
        } else if (platform != null) {
            shops = shopService.findByPlatform(platform);
        } else {
            shops = shopService.findAll();
        }
        return ApiResponse.success(shops.stream()
                .map(shop -> ShopResponse.from(shop, userService.findById(shop.getUserId()).orElse(null)))
                .toList());
    }

    @GetMapping("/my")
    public ApiResponse<List<Shop>> myShops() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(shopService.findByUserId(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShopResponse> get(@PathVariable Long id) {
        return shopService.findById(id)
                .map(shop -> ApiResponse.success(ShopResponse.from(shop, userService.findById(shop.getUserId()).orElse(null))))
                .orElse(ApiResponse.error("店铺不存在"));
    }

    @PostMapping
    public ApiResponse<Shop> create(@RequestBody Shop shop) {
        return ApiResponse.success(shopService.create(shop));
    }

    @PostMapping("/pending")
    public ApiResponse<Shop> createPending(@RequestBody Shop shop) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (shopService.hasPendingShop(userId, shop.getPlatform(), "NEW-")) {
            return ApiResponse.error("您已添加新店铺但未成功登录，请先完成登录后再添加");
        }
        shop.setId(null);
        shop.setUserId(userId);
        shop.setRemainingDays(shop.getRemainingDays() == null ? 30 : shop.getRemainingDays());
        shop.setAutoRenew(Boolean.TRUE.equals(shop.getAutoRenew()));
        return ApiResponse.success(shopService.create(shop));
    }

    @PutMapping("/{id}")
    public ApiResponse<Shop> update(@PathVariable Long id, @RequestBody Shop shop) {
        return ApiResponse.success(shopService.update(id, shop));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        shopService.delete(id);
        return ApiResponse.success();
    }
}
