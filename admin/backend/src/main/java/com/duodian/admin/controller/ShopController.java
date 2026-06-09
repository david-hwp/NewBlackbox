package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.CloneShopCreateRequest;
import com.duodian.admin.controller.dto.CloneShopCreateResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.PendingShopDeductResponse;
import com.duodian.admin.controller.dto.ShopRenewRequest;
import com.duodian.admin.controller.dto.ShopRenewResponse;
import com.duodian.admin.controller.dto.ShopAuthTokenRequest;
import com.duodian.admin.controller.dto.ShopReportRequest;
import com.duodian.admin.controller.dto.ShopResponse;
import com.duodian.admin.entity.ComputeDeduction;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.CloneAuthorizationTokenService;
import com.duodian.admin.service.ComputeService;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/shops")
public class ShopController {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_AUTH_DAYS = 30;

    private final ShopService shopService;
    private final UserService userService;
    private final ComputeService computeService;
    private final CloneAuthorizationTokenService cloneAuthorizationTokenService;
    private final PermissionService permissionService;

    public ShopController(
            ShopService shopService,
            UserService userService,
            ComputeService computeService,
            CloneAuthorizationTokenService cloneAuthorizationTokenService,
            PermissionService permissionService
    ) {
        this.shopService = shopService;
        this.userService = userService;
        this.computeService = computeService;
        this.cloneAuthorizationTokenService = cloneAuthorizationTokenService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String packageName,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String userKeyword,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        permissionService.requireAdminRole();
        User currentUser = permissionService.currentUser();
        Long effectiveChannelId = permissionService.filterChannelForQuery(channelId);
        String packageFilter = normalize(packageName);
        boolean pagedRequest = page != null || size != null || hasText(platform) || hasText(phone)
                || hasText(userKeyword) || hasText(shopName) || effectiveChannelId != null;
        if (pagedRequest) {
            int pageNumber = Math.max(1, page == null ? 1 : page);
            int pageSize = Math.max(1, Math.min(100, size == null ? 10 : size));
            Long effectiveUserId = userId;
            Page<ShopResponse> shops = shopService.search(
                            effectiveUserId,
                            effectiveChannelId,
                            packageFilter,
                            normalize(platform),
                            normalize(phone),
                            normalize(userKeyword),
                            normalize(shopName),
                            PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
                    )
                    .map(shop -> ShopResponse.from(shop, userService.findById(shop.getUserId()).orElse(null)));
            return ApiResponse.success(PagedResponse.from(shops));
        }

        List<Shop> shops;
        if (effectiveChannelId != null) {
            shops = shopService.findByChannelId(effectiveChannelId);
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
    public ApiResponse<List<ShopResponse>> myShops() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        User user = userService.findById(userId).orElse(null);
        return ApiResponse.success(shopService.findByUserId(userId).stream()
                .map(shop -> ShopResponse.from(shop, user))
                .toList());
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
            return ApiResponse.error(403, "请使用店铺创建接口新增店铺");
        }
        permissionService.requireActiveChannelForMutation(shop.getChannelId());
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
        return ApiResponse.error("请使用店铺创建接口新增店铺");
    }

    @PostMapping("/pending-deduct")
    @Transactional
    public ApiResponse<PendingShopDeductResponse> createPendingWithDeduction(@RequestBody ShopReportRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.error("请使用店铺创建接口新增店铺");
    }

    @PostMapping("/clone/create")
    @Transactional
    public ApiResponse<CloneShopCreateResponse> createCloneShop(@RequestBody CloneShopCreateRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (request == null) {
            return ApiResponse.error("请求不能为空");
        }
        String operationKey = normalize(request.getOperationKey());
        if (operationKey == null) {
            return ApiResponse.error("缺少操作标识，无法新增");
        }
        Optional<ComputeDeduction> existingOperation = computeService.findExistingCloneCreateOperation(userId, operationKey);
        if (existingOperation.isPresent()) {
            Optional<Shop> existingShop = shopService.findByUserIdAndCloneInstanceId(
                    userId,
                    existingOperation.get().getCloneInstanceId()
            );
            if (existingShop.isPresent()) {
                User user = userService.refreshShopStats(userId);
                String token = cloneAuthorizationTokenService.signToken(existingShop.get(), user);
                return ApiResponse.success(CloneShopCreateResponse.from(
                        existingShop.get(),
                        user,
                        false,
                        token,
                        cloneAuthorizationTokenService.getPublicKeyId()
                ));
            }
            return ApiResponse.error("该创建请求状态异常，请刷新后重试");
        }

        String platform = normalize(request.getPlatform());
        String packageName = normalize(request.getPackageName());
        Integer localVirtualUserId = request.getLocalVirtualUserId();
        if (packageName == null) {
            return ApiResponse.error("缺少应用包名，无法新增");
        }
        if (localVirtualUserId == null || localVirtualUserId < 0) {
            return ApiResponse.error("缺少虚拟用户目录号，无法扣减算力");
        }

        String displayPlatform = platform == null ? packageName : platform;
        User currentUser = computeService.lockActiveUser(userId).orElse(null);
        if (currentUser == null) {
            return ApiResponse.error("用户不存在");
        }

        String phone = firstNonBlank(currentUser.getPhone(), "unknown");
        int cloneSequence = (int) shopService.countByUserIdAndPackageName(userId, packageName) + 1;
        String validationCode;
        String cloneInstanceId;
        do {
            validationCode = randomHex(16);
            cloneInstanceId = buildReadableCloneInstanceId(
                    phone,
                    packageName,
                    cloneSequence,
                    localVirtualUserId,
                    validationCode
            );
        } while (shopService.findByCloneInstanceId(cloneInstanceId).isPresent());

        String shopName = "新增店铺-[" + cloneSequence + "]";
        ComputeService.DeductionResult deduction = computeService.deductComputeForCloneCreate(
                userId,
                cloneInstanceId,
                operationKey,
                displayPlatform,
                shopName
        );
        if (!deduction.isSuccess()) {
            return ApiResponse.error(402, "算力余额不足");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusDays(DEFAULT_AUTH_DAYS);
        Shop shop = new Shop();
        shop.setUserId(userId);
        shop.setShopName(shopName);
        shop.setShopId("NEW-" + sha256(cloneInstanceId).substring(0, 32));
        shop.setPlatform(displayPlatform);
        shop.setPlatformName(firstNonBlank(request.getPlatformName(), displayPlatform));
        shop.setPackageName(packageName);
        shop.setRemainingDays(DEFAULT_AUTH_DAYS);
        shop.setAutoRenew(Boolean.TRUE.equals(request.getAutoRenew()));
        shop.setCloneInstanceId(cloneInstanceId);
        shop.setCloneSequence(cloneSequence);
        shop.setLocalVirtualUserId(localVirtualUserId);
        shop.setCloneValidationCode(validationCode);
        shop.setCloneValidationHash(sha256(cloneInstanceId + ":" + validationCode));
        shop.setCredentialVersion(1);
        shop.setAuthStartAt(now);
        shop.setAuthExpireAt(expireAt);
        shop.setAuthorizationJti(randomHex(16));
        shop.setLastDeductedAt(now);
        shop.setExpireAt(expireAt);

        Shop saved = shopService.create(shop);
        User user = userService.refreshShopStats(userId);
        String token = cloneAuthorizationTokenService.signToken(saved, user);
        return ApiResponse.success(CloneShopCreateResponse.from(
                saved,
                user,
                deduction.isDeducted(),
                token,
                cloneAuthorizationTokenService.getPublicKeyId()
        ));
    }

    @PutMapping("/{id}")
    public ApiResponse<Shop> update(@PathVariable Long id, @RequestBody Shop shop) {
        if (!canAccessShop(id)) {
            return ApiResponse.error("店铺不存在");
        }
        Long ownerId = shopService.findById(id).map(Shop::getUserId).orElse(null);
        if (!isCurrentUserAdmin()) {
            shop.setUserId(AuthContext.getUserId());
        } else {
            Shop existing = shopService.findById(id).orElseThrow(() -> new RuntimeException("店铺不存在"));
            permissionService.requireActiveChannelForMutation(existing.getChannelId());
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
    public ApiResponse<ShopRenewResponse> renew(@PathVariable Long id, @RequestBody(required = false) ShopRenewRequest request) {
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
        String cloneInstanceId = normalize(shop.getCloneInstanceId());
        if (cloneInstanceId == null) {
            return ApiResponse.error("店铺缺少店铺标识，无法续期");
        }
        String operationKey = normalize(request != null ? request.getOperationKey() : null);
        if (operationKey == null) {
            operationKey = "renew-" + id + "-" + System.currentTimeMillis();
        }
        ComputeService.DeductionResult deduction = computeService.deductComputeForCloneRenew(
                userId,
                cloneInstanceId,
                operationKey,
                shop.getPlatform(),
                shop.getShopName()
        );
        if (!deduction.isSuccess()) {
            return ApiResponse.error(402, "算力余额不足");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = shop.getAuthExpireAt() != null && shop.getAuthExpireAt().isAfter(now)
                ? shop.getAuthExpireAt()
                : now;
        LocalDateTime expireAt = base.plusDays(DEFAULT_AUTH_DAYS);
        shop.setRemainingDays(DEFAULT_AUTH_DAYS);
        shop.setExpireAt(expireAt);
        shop.setAuthStartAt(shop.getAuthStartAt() == null ? now : shop.getAuthStartAt());
        shop.setAuthExpireAt(expireAt);
        shop.setCredentialVersion((shop.getCredentialVersion() == null ? 1 : shop.getCredentialVersion()) + 1);
        shop.setAuthorizationJti(randomHex(16));
        shop.setLastDeductedAt(now);
        Shop saved = shopService.update(shop.getId(), shop);
        User user = userService.refreshShopStats(userId);
        String token = cloneAuthorizationTokenService.signToken(saved, user);
        return ApiResponse.success(ShopRenewResponse.from(saved, user, token, cloneAuthorizationTokenService.getPublicKeyId()));
    }

    @PostMapping("/{id}/auth-token")
    public ApiResponse<CloneShopCreateResponse> issueAuthorizationToken(
            @PathVariable Long id,
            @RequestBody(required = false) ShopAuthTokenRequest request
    ) {
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
        if (normalize(shop.getCloneInstanceId()) == null) {
            return ApiResponse.error("店铺缺少店铺标识，无法授权");
        }
        Integer requestedUserId = request != null ? request.getLocalVirtualUserId() : null;
        if (requestedUserId != null) {
            if (requestedUserId < 0) {
                return ApiResponse.error("虚拟用户目录号无效");
            }
            shop.setLocalVirtualUserId(requestedUserId);
        }
        String requestedPackageName = normalize(request != null ? request.getPackageName() : null);
        if (requestedPackageName != null) {
            shop.setPackageName(requestedPackageName);
        }
        if (shop.getLocalVirtualUserId() == null || shop.getLocalVirtualUserId() < 0) {
            return ApiResponse.error("店铺缺少虚拟用户目录号，无法授权");
        }
        if (normalize(shop.getPackageName()) == null) {
            return ApiResponse.error("店铺缺少应用包名，无法授权");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = firstPresent(shop.getAuthExpireAt(), shop.getExpireAt());
        boolean legacyWithoutAuthorizationWindow = expireAt == null;
        if (legacyWithoutAuthorizationWindow) {
            expireAt = now.plusDays(DEFAULT_AUTH_DAYS);
            shop.setRemainingDays(DEFAULT_AUTH_DAYS);
        } else if (!expireAt.isAfter(now)) {
            return ApiResponse.error(402, "店铺已到期，请续期后再打开");
        }
        if (shop.getAuthStartAt() == null) {
            shop.setAuthStartAt(now);
        }
        shop.setAuthExpireAt(expireAt);
        shop.setExpireAt(expireAt);
        if (shop.getCredentialVersion() == null || shop.getCredentialVersion() < 1) {
            shop.setCredentialVersion(1);
        }
        if (normalize(shop.getAuthorizationJti()) == null) {
            shop.setAuthorizationJti(randomHex(16));
        }
        Shop saved = shopService.update(shop.getId(), shop);
        User user = userService.refreshShopStats(userId);
        String token = cloneAuthorizationTokenService.signToken(saved, user);
        return ApiResponse.success(CloneShopCreateResponse.from(
                saved,
                user,
                false,
                token,
                cloneAuthorizationTokenService.getPublicKeyId()
        ));
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
        return user != null && ("ADMIN".equalsIgnoreCase(user.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(user.getRole())
                || "CHANNEL".equalsIgnoreCase(user.getRole()));
    }

    private boolean canAccessShop(Long id) {
        return shopService.findById(id).map(this::canAccessShop).orElse(false);
    }

    private boolean canAccessShop(Shop shop) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        if (currentUser.getId().equals(shop.getUserId())) {
            return true;
        }
        if (!isAdmin(currentUser)) {
            return false;
        }
        try {
            permissionService.requireChannelAccess(shop.getChannelId());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime firstPresent(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private String buildReadableCloneInstanceId(
            String phone,
            String packageName,
            int cloneSequence,
            int localVirtualUserId,
            String validationCode
    ) {
        String readablePhone = normalizePhoneForCloneId(phone);
        String readablePackage = normalizePackageForCloneId(packageName);
        String randomDigest = sha256(validationCode).substring(0, 8);
        return "CLN1-" + readablePhone
                + "-" + readablePackage
                + "-N" + cloneSequence
                + "-U" + localVirtualUserId
                + "-R" + randomDigest;
    }

    private String normalizePhoneForCloneId(String phone) {
        String normalized = firstNonBlank(phone, "unknown").replaceAll("[^0-9A-Za-z]", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String normalizePackageForCloneId(String packageName) {
        String normalized = firstNonBlank(packageName, "unknown").replaceAll("[^0-9A-Za-z._]", "_");
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 64) + "." + sha256(normalized).substring(0, 8);
    }

    private String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(byteCount * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
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
