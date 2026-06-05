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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        String shopId = normalize(request.getShopId());
        String platform = normalize(request.getPlatform());
        String cloneInstanceId = normalize(request.getCloneInstanceId());
        boolean hasRealShopId = isRealShopId(shopId);
        Optional<Shop> existing = Optional.empty();

        if (hasRealShopId) {
            existing = platform == null
                    ? Optional.empty()
                    : shopService.findByUserIdAndShopIdAndPlatform(userId, shopId, platform);
            if (existing.isEmpty() && cloneInstanceId != null) {
                Optional<Shop> cloneOwner = shopService.findByUserIdAndCloneInstanceId(userId, cloneInstanceId);
                if (cloneOwner.isPresent() && isDifferentRealShop(cloneOwner.get(), shopId, platform)) {
                    String switchedCloneInstanceId = buildSwitchedCloneInstanceId(cloneInstanceId, platform, shopId);
                    Optional<Shop> switchedShop = shopService.findByUserIdAndCloneInstanceId(userId, switchedCloneInstanceId);
                    if (switchedShop.isPresent()) {
                        existing = switchedShop;
                    } else {
                        return createNewShop(userId, request, switchedCloneInstanceId, true);
                    }
                } else {
                    existing = cloneOwner;
                }
            }
            if (existing.isEmpty()) {
                existing = shopService.findPendingByUserPackageAndPlatform(
                        userId,
                        request.getPackageName(),
                        request.getPlatform()
                );
            }
        } else {
            if (cloneInstanceId != null) {
                existing = shopService.findByUserIdAndCloneInstanceId(userId, cloneInstanceId);
            }
            if (existing.isEmpty() && "-".equals(shopId)) {
                existing = shopService.findByUserIdAndPackageNameAndPlatform(
                        userId,
                        request.getPackageName(),
                        request.getPlatform()
                );
            } else if (existing.isEmpty()) {
                existing = shopService.findByUserIdAndShopId(userId, request.getShopId());
            }
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
                if (isPendingSwitchShopId(shop.getShopId())) {
                    String expectedPendingShopId = buildPendingSwitchShopId(
                            normalize(request.getPlatform()) == null ? shop.getPlatform() : request.getPlatform(),
                            request.getShopId()
                    );
                    if (!shop.getShopId().equals(expectedPendingShopId)) {
                        return ApiResponse.error("请在新分身中切换到对应店铺后重试");
                    }
                }
                if (shop.getLastDeductedAt() == null) {
                    boolean deducted = computeService.deductCompute(userId, request.getShopId(), request.getShopName(), request.getPlatform());
                    if (!deducted) {
                        return ApiResponse.error(402, "算力余额不足");
                    }
                    shop.setLastDeductedAt(LocalDateTime.now());
                    result.put("deducted", true);
                } else {
                    result.put("deducted", false);
                }
                shop.setShopId(request.getShopId());
                shop.setExpireAt(LocalDateTime.now().plusDays(30));
                result.put("isNew", true);
            } else {
                result.put("deducted", false);
                result.put("isNew", false);
            }
            fillShopFromRequest(shop, request);
            if (cloneInstanceId != null && canAssignCloneInstanceId(userId, shop.getId(), cloneInstanceId)) {
                shop.setCloneInstanceId(cloneInstanceId);
            }
            shopService.update(shop.getId(), shop);
            result.put("cloneInstanceId", shop.getCloneInstanceId());
        } else {
            return createNewShop(userId, request, cloneInstanceId, false);
        }

        fillUserStats(result, userId);
        result.put("switchedShop", false);

        return ApiResponse.success(result);
    }

    private ApiResponse<Map<String, Object>> createNewShop(
            Long userId,
            ShopReportRequest request,
            String cloneInstanceId,
            boolean switchedShop
    ) {
        boolean deducted = computeService.deductCompute(
                userId,
                request.getShopId(),
                request.getShopName(),
                request.getPlatform()
        );
        if (!deducted) {
            return ApiResponse.error(402, "算力余额不足");
        }

        Shop shop = new Shop();
        shop.setUserId(userId);
        fillShopFromRequest(shop, request);
        shop.setCloneInstanceId(cloneInstanceId);
        shop.setExpireAt(LocalDateTime.now().plusDays(30));
        shop.setLastDeductedAt(LocalDateTime.now());
        shopService.create(shop);

        Map<String, Object> result = new HashMap<>();
        result.put("shopId", request.getShopId());
        result.put("cloneInstanceId", shop.getCloneInstanceId());
        result.put("deducted", true);
        result.put("isNew", true);
        result.put("switchedShop", switchedShop);
        if (switchedShop) {
            result.put("message", "检测到店铺切换，已创建新店铺并扣划算力");
        }
        fillUserStats(result, userId);
        return ApiResponse.success(result);
    }

    private void fillShopFromRequest(Shop shop, ShopReportRequest request) {
        shop.setShopName(request.getShopName());
        shop.setShopId(request.getShopId());
        shop.setPlatform(request.getPlatform());
        shop.setPlatformName(request.getPlatformName());
        shop.setPackageName(request.getPackageName());
        shop.setRemainingDays(request.getRemainingDays());
        shop.setAutoRenew(request.getAutoRenew());
    }

    private void fillUserStats(Map<String, Object> result, Long userId) {
        User user = userService.refreshShopStats(userId);
        result.put("balance", user != null ? user.getComputeBalance() : 0);
        result.put("shopCount", user != null ? user.getShopCount() : 0);
        result.put("platformCount", user != null ? user.getPlatformCount() : 0);
    }

    private boolean canAssignCloneInstanceId(Long userId, Long currentShopId, String cloneInstanceId) {
        Optional<Shop> owner = shopService.findByUserIdAndCloneInstanceId(userId, cloneInstanceId);
        return owner.isEmpty() || owner.get().getId().equals(currentShopId);
    }

    private boolean isDifferentRealShop(Shop existing, String shopId, String platform) {
        if (!isRealShopId(existing.getShopId())) {
            return false;
        }
        return !equalsNormalized(existing.getShopId(), shopId)
                || !equalsNormalized(existing.getPlatform(), platform);
    }

    private boolean equalsNormalized(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft == null) {
            return normalizedRight == null;
        }
        return normalizedLeft.equals(normalizedRight);
    }

    private String buildSwitchedCloneInstanceId(String cloneInstanceId, String platform, String shopId) {
        String source = cloneInstanceId + ":" + normalize(platform) + ":" + normalize(shopId);
        return "clone-switch-" + sha256(source);
    }

    private boolean isPendingSwitchShopId(String shopId) {
        String normalized = normalize(shopId);
        return normalized != null && normalized.startsWith("NEW-SWITCH-");
    }

    private String buildPendingSwitchShopId(String platform, String shopId) {
        return "NEW-SWITCH-" + normalize(platform) + "-" + sha256(normalize(shopId)).substring(0, 16);
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
