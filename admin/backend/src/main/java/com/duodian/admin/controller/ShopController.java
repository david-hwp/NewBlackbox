package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PendingShopDeductResponse;
import com.duodian.admin.controller.dto.ShopRenewResponse;
import com.duodian.admin.controller.dto.ShopReportRequest;
import com.duodian.admin.controller.dto.ShopResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ComputeService;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/shops")
public class ShopController {

    private final ShopService shopService;
    private final UserService userService;
    private final ComputeService computeService;

    public ShopController(ShopService shopService, UserService userService, ComputeService computeService) {
        this.shopService = shopService;
        this.userService = userService;
        this.computeService = computeService;
    }

    @GetMapping
    public ApiResponse<List<ShopResponse>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String packageName) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ApiResponse.error(401, "未登录");
        }
        String packageFilter = normalize(packageName);

        List<Shop> shops;
        if (!isAdmin(currentUser)) {
            shops = packageFilter != null
                    ? shopService.findByUserIdAndPackageName(currentUser.getId(), packageFilter)
                    : shopService.findByUserId(currentUser.getId());
        } else if (userId != null) {
            shops = packageFilter != null
                    ? shopService.findByUserIdAndPackageName(userId, packageFilter)
                    : shopService.findByUserId(userId);
        } else if (packageFilter != null) {
            shops = shopService.findByPackageName(packageFilter);
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
        String packageName = normalize(shop.getPackageName());
        if (packageName == null) {
            return ApiResponse.error("缺少应用包名，无法添加店铺卡片");
        }
        if (shopService.hasPendingShopByPackage(userId, packageName, "NEW-")) {
            return ApiResponse.error("您已添加新店铺但未成功登录，请先完成登录后再添加");
        }
        shop.setId(null);
        shop.setUserId(userId);
        shop.setPackageName(packageName);
        shop.setRemainingDays(shop.getRemainingDays() == null ? 30 : shop.getRemainingDays());
        shop.setAutoRenew(Boolean.TRUE.equals(shop.getAutoRenew()));
        Shop saved = shopService.create(shop);
        userService.refreshShopStats(userId);
        return ApiResponse.success(saved);
    }

    @PostMapping("/pending-deduct")
    @Transactional
    public ApiResponse<PendingShopDeductResponse> createPendingWithDeduction(@RequestBody ShopReportRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        String detectedShopId = normalize(request.getShopId());
        String platform = normalize(request.getPlatform());
        String packageName = normalize(request.getPackageName());
        if (!isRealShopId(detectedShopId) || packageName == null) {
            return ApiResponse.error("店铺信息无效，无法新增");
        }
        String displayPlatform = platform == null ? packageName : platform;

        if (shopService.findByUserIdAndShopIdAndPackageName(userId, detectedShopId, packageName).isPresent()) {
            return ApiResponse.error("该店铺已添加");
        }

        String pendingShopId = buildPendingSwitchShopId(packageName, detectedShopId);
        Optional<Shop> existingPending = shopService.findByUserIdAndShopIdAndPackageName(userId, pendingShopId, packageName);
        if (existingPending.isPresent()) {
            User user = userService.refreshShopStats(userId);
            return ApiResponse.success(PendingShopDeductResponse.from(existingPending.get(), user, false));
        }

        boolean deducted = computeService.deductCompute(
                userId,
                detectedShopId,
                firstNonBlank(request.getShopName(), request.getPlatformName(), displayPlatform + "-" + detectedShopId),
                displayPlatform
        );
        if (!deducted) {
            return ApiResponse.error(402, "算力余额不足");
        }

        LocalDateTime now = LocalDateTime.now();
        Shop shop = new Shop();
        shop.setUserId(userId);
        shop.setShopName(firstNonBlank(request.getShopName(), request.getPlatformName(), displayPlatform + "-" + detectedShopId));
        shop.setShopId(pendingShopId);
        shop.setPlatform(displayPlatform);
        shop.setPlatformName(firstNonBlank(request.getPlatformName(), displayPlatform));
        shop.setPackageName(packageName);
        shop.setRemainingDays(request.getRemainingDays() == null ? 30 : request.getRemainingDays());
        shop.setAutoRenew(Boolean.TRUE.equals(request.getAutoRenew()));
        shop.setLastDeductedAt(now);
        shop.setExpireAt(now.plusDays(30));

        Shop saved = shopService.create(shop);
        User user = userService.refreshShopStats(userId);
        return ApiResponse.success(PendingShopDeductResponse.from(saved, user, true));
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

    @PostMapping("/{id}/renew")
    public ApiResponse<ShopRenewResponse> renew(@PathVariable Long id) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Shop shop = shopService.findById(id)
                .filter(this::canAccessShop)
                .orElse(null);
        if (shop == null) {
            return ApiResponse.error("店铺不存在");
        }
        boolean deducted = computeService.deductComputeForRenewal(
                userId,
                shop.getShopId(),
                shop.getShopName(),
                shop.getPlatform()
        );
        if (!deducted) {
            return ApiResponse.error(402, "算力余额不足");
        }
        shop.setRemainingDays(30);
        shop.setExpireAt(LocalDateTime.now().plusDays(30));
        shop.setLastDeductedAt(LocalDateTime.now());
        Shop saved = shopService.update(shop.getId(), shop);
        User user = userService.refreshShopStats(userId);
        return ApiResponse.success(ShopRenewResponse.from(saved, user));
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }

    private boolean isRealShopId(String shopId) {
        return shopId != null
                && !shopId.isBlank()
                && !"-".equals(shopId)
                && !shopId.startsWith("NEW-");
    }

    private String buildPendingSwitchShopId(String packageName, String shopId) {
        return "NEW-SWITCH-" + sha256(packageName).substring(0, 10) + "-" + sha256(shopId).substring(0, 16);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
