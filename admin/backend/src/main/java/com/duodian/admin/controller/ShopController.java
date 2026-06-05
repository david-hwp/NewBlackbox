package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ShopResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
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
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ApiResponse.error(401, "未登录");
        }

        List<Shop> shops;
        if (!isAdmin(currentUser)) {
            shops = platform != null
                    ? shopService.findByUserIdAndPlatform(currentUser.getId(), platform)
                    : shopService.findByUserId(currentUser.getId());
        } else if (userId != null) {
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
                .filter(this::canAccessShop)
                .map(shop -> ApiResponse.success(ShopResponse.from(shop, userService.findById(shop.getUserId()).orElse(null))))
                .orElse(ApiResponse.error("店铺不存在"));
    }

    @PostMapping
    public ApiResponse<Shop> create(@RequestBody Shop shop) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (!isAdmin(currentUser)) {
            shop.setUserId(currentUser.getId());
        }
        Shop saved = shopService.create(shop);
        userService.refreshShopStats(saved.getUserId());
        return ApiResponse.success(saved);
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
        Shop saved = shopService.create(shop);
        userService.refreshShopStats(userId);
        return ApiResponse.success(saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<Shop> update(@PathVariable Long id, @RequestBody Shop shop) {
        if (!canAccessShop(id)) {
            return ApiResponse.error("店铺不存在");
        }
        Long ownerId = shopService.findById(id).map(Shop::getUserId).orElse(null);
        if (!isCurrentUserAdmin()) {
            shop.setUserId(AuthContext.getUserId());
        }
        Shop saved = shopService.update(id, shop);
        userService.refreshShopStats(saved.getUserId());
        if (ownerId != null && !ownerId.equals(saved.getUserId())) {
            userService.refreshShopStats(ownerId);
        }
        return ApiResponse.success(saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!canAccessShop(id)) {
            return ApiResponse.error("店铺不存在");
        }
        Long ownerId = shopService.findById(id).map(Shop::getUserId).orElse(null);
        shopService.delete(id);
        if (ownerId != null) {
            userService.refreshShopStats(ownerId);
        }
        return ApiResponse.success();
    }

    private User getCurrentUser() {
        Long currentUserId = AuthContext.getUserId();
        if (currentUserId == null) {
            return null;
        }
        return userService.findById(currentUserId).orElse(null);
    }

    private boolean isCurrentUserAdmin() {
        User currentUser = getCurrentUser();
        return isAdmin(currentUser);
    }

    private boolean isAdmin(User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private boolean canAccessShop(Long id) {
        return shopService.findById(id).map(this::canAccessShop).orElse(false);
    }

    private boolean canAccessShop(Shop shop) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        return isAdmin(currentUser) || currentUser.getId().equals(shop.getUserId());
    }
}
