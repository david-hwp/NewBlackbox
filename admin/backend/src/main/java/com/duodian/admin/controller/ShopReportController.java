package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ShopReportRequest;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/shops")
public class ShopReportController {

    private static final Pattern CLONE_RANDOM_DIGEST_PATTERN = Pattern.compile(".*-R([0-9a-fA-F]{8})$");

    private final ShopService shopService;
    private final UserService userService;

    public ShopReportController(ShopService shopService, UserService userService) {
        this.shopService = shopService;
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
        String packageName = normalize(request.getPackageName());
        String cloneInstanceId = normalize(request.getCloneInstanceId());
        String shopName = normalize(request.getShopName());
        if (packageName == null) {
            return ApiResponse.error("缺少应用包名，无法处理店铺");
        }
        boolean hasRealShopId = isRealShopId(shopId);
        boolean hasVerifiedIdentity = hasRealShopId && isVerifiedShopName(shopName);
        Optional<Shop> existing = Optional.empty();

        if (cloneInstanceId != null) {
            existing = shopService.findByUserIdAndCloneInstanceId(userId, cloneInstanceId);
        }
        if (hasVerifiedIdentity) {
            if (existing.isEmpty()) {
                existing = shopService.findByUserIdAndShopIdAndPackageName(userId, shopId, packageName);
            }
            if (existing.isEmpty()) {
                existing = shopService.findPendingByUserPackage(userId, packageName);
            }
        } else {
            if (existing.isEmpty() && "-".equals(shopId)) {
                existing = shopService.findPendingByUserPackage(userId, packageName);
            } else if (existing.isEmpty()) {
                existing = shopService.findByUserIdAndShopIdAndPackageName(userId, request.getShopId(), packageName);
            }
        }

        if (existing.isEmpty()) {
            return ApiResponse.error("待登录店铺不存在，请先添加店铺卡片");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("cloneInstanceId", cloneInstanceId);

        Shop shop = existing.get();
        if (!validateCloneOwnership(shop, cloneInstanceId)) {
            return ApiResponse.error(403, "店铺标识校验失败");
        }
        if (!validateLocalVirtualUser(shop, request.getLocalVirtualUserId())) {
            return ApiResponse.error(403, "虚拟用户目录号校验失败");
        }
        boolean wasPending = shop.getShopId() != null && shop.getShopId().startsWith("NEW-");
        if (hasVerifiedIdentity) {
            Optional<Shop> duplicate = shopService.findByUserIdAndShopIdAndPackageName(userId, shopId, packageName);
            if (duplicate.isPresent() && !duplicate.get().getId().equals(shop.getId())) {
                return ApiResponse.error("该店铺已添加");
            }
            if (shop.getExpireAt() == null) {
                shop.setExpireAt(LocalDateTime.now().plusDays(30));
            }
        }
        result.put("deducted", false);
        result.put("isNew", wasPending && hasVerifiedIdentity);

        fillShopFromRequest(shop, request, hasVerifiedIdentity);
        if (cloneInstanceId != null && canAssignCloneInstanceId(userId, shop.getId(), cloneInstanceId)) {
            shop.setCloneInstanceId(cloneInstanceId);
        }
        shopService.update(shop.getId(), shop);
        result.put("shopId", shop.getShopId());
        result.put("cloneInstanceId", shop.getCloneInstanceId());

        fillUserStats(result, userId);
        result.put("switchedShop", false);

        return ApiResponse.success(result);
    }

    private void fillShopFromRequest(Shop shop, ShopReportRequest request, boolean hasVerifiedIdentity) {
        if (hasVerifiedIdentity) {
            shop.setShopName(normalize(request.getShopName()));
            shop.setShopId(normalize(request.getShopId()));
            shop.setIdentityVerified(true);
            shop.setIdentityVerifiedAt(LocalDateTime.now());
        }
        shop.setPlatform(normalize(request.getPlatform()));
        shop.setPlatformName(normalize(request.getPlatformName()));
        shop.setPackageName(normalize(request.getPackageName()));
        shop.setRemainingDays(request.getRemainingDays());
        shop.setAutoRenew(request.getAutoRenew());
        if (request.getLocalVirtualUserId() != null) {
            shop.setLocalVirtualUserId(request.getLocalVirtualUserId());
        }
    }

    private boolean validateLocalVirtualUser(Shop shop, Integer requestUserId) {
        if (requestUserId == null) {
            return true;
        }
        if (requestUserId < 0) {
            return false;
        }
        Integer storedUserId = shop.getLocalVirtualUserId();
        return storedUserId == null || storedUserId.equals(requestUserId);
    }

    private void fillUserStats(Map<String, Object> result, Long userId) {
        User user = userService.refreshShopStats(userId);
        result.put("balance", user != null ? user.getComputeBalance() : 0);
        result.put("shopCount", user != null ? user.getShopCount() : 0);
        result.put("platformCount", user != null ? user.getPlatformCount() : 0);
    }

    private boolean canAssignCloneInstanceId(Long userId, Long currentShopId, String cloneInstanceId) {
        Optional<Shop> owner = shopService.findByCloneInstanceId(cloneInstanceId);
        return owner.isEmpty()
                || (owner.get().getId().equals(currentShopId) && owner.get().getUserId().equals(userId));
    }

    private boolean validateCloneOwnership(Shop shop, String requestCloneInstanceId) {
        String storedCloneInstanceId = normalize(shop.getCloneInstanceId());
        if (storedCloneInstanceId == null) {
            return true;
        }
        if (!storedCloneInstanceId.equals(requestCloneInstanceId)) {
            return false;
        }
        String validationCode = normalize(shop.getCloneValidationCode());
        String validationHash = normalize(shop.getCloneValidationHash());
        if (validationCode == null && validationHash == null) {
            return true;
        }
        if (validationCode == null || validationHash == null) {
            return false;
        }
        Matcher matcher = CLONE_RANDOM_DIGEST_PATTERN.matcher(storedCloneInstanceId);
        if (!matcher.matches()) {
            return false;
        }
        String digestInCloneId = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!sha256(validationCode).startsWith(digestInCloneId)) {
            return false;
        }
        String expectedHash = sha256(storedCloneInstanceId + ":" + validationCode);
        return expectedHash.equalsIgnoreCase(validationHash);
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
                && !shopId.startsWith("NEW-")
                && !shopId.startsWith("phase13-");
    }

    private boolean isVerifiedShopName(String shopName) {
        return shopName != null
                && !shopName.isBlank()
                && !shopName.startsWith("NEW-")
                && !shopName.startsWith("phase13-")
                && !shopName.startsWith("新增店铺-[")
                && !shopName.startsWith("User[")
                && !shopName.startsWith("未知");
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
