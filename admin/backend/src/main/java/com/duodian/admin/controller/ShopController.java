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
import com.duodian.admin.controller.dto.ShopAuthorizationProbeResponse;
import com.duodian.admin.controller.dto.ShopOrderRequest;
import com.duodian.admin.controller.dto.ShopReportRequest;
import com.duodian.admin.controller.dto.ShopResponse;
import com.duodian.admin.entity.ComputeDeduction;
import com.duodian.admin.entity.PlatformConfig;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.PlatformConfigRepository;
import com.duodian.admin.service.CloneAuthorizationTokenService;
import com.duodian.admin.service.ComputeService;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/shops")
public class ShopController {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_AUTH_DAYS = 30;
    private static final int MAX_LOGIN_STATE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LOGIN_STATE_MANIFEST_BYTES = 16 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ShopService shopService;
    private final UserService userService;
    private final ComputeService computeService;
    private final CloneAuthorizationTokenService cloneAuthorizationTokenService;
    private final PermissionService permissionService;
    private final PlatformConfigRepository platformConfigRepository;
    private final HttpClient authorizationHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${app.zr.control-url:http://100.99.88.6:14501}")
    private String zrControlUrl = "http://100.99.88.6:14501";

    @Value("${app.zr.stream-url:http://100.99.88.6:14500/}")
    private String zrStreamUrl = "http://100.99.88.6:14500/";

    public ShopController(
            ShopService shopService,
            UserService userService,
            ComputeService computeService,
            CloneAuthorizationTokenService cloneAuthorizationTokenService,
            PermissionService permissionService,
            PlatformConfigRepository platformConfigRepository
    ) {
        this.shopService = shopService;
        this.userService = userService;
        this.computeService = computeService;
        this.cloneAuthorizationTokenService = cloneAuthorizationTokenService;
        this.permissionService = permissionService;
        this.platformConfigRepository = platformConfigRepository;
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
            boolean includeShopAuthorizationUrl = isCurrentUserSuperAdmin();
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
                    .map(shop -> ShopResponse.from(
                            shop,
                            userService.findById(shop.getUserId()).orElse(null),
                            includeShopAuthorizationUrl
                    ));
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
        boolean includeShopAuthorizationUrl = isCurrentUserSuperAdmin();
        return ApiResponse.success(shops.stream()
                .map(shop -> ShopResponse.from(
                        shop,
                        userService.findById(shop.getUserId()).orElse(null),
                        includeShopAuthorizationUrl
                ))
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
                .map(shop -> ApiResponse.success(ShopResponse.from(
                        shop,
                        userService.findById(shop.getUserId()).orElse(null),
                        isCurrentUserSuperAdmin()
                )))
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
        if (currentUser.isSubscriptionActive() && currentUser.getSubscriptionExpiresAt() != null) {
            expireAt = currentUser.getSubscriptionExpiresAt();
        }
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
        Optional<Shop> existingOptional = shopService.findById(id);
        if (existingOptional.isEmpty() || !canAccessShop(existingOptional.get())) {
            return ApiResponse.error("店铺不存在");
        }
        Shop existing = existingOptional.get();
        shop.setIdentityVerified(existing.getIdentityVerified());
        shop.setIdentityVerifiedAt(existing.getIdentityVerifiedAt());
        Long ownerId = existing.getUserId();
        if (!isCurrentUserAdmin()) {
            shop.setUserId(AuthContext.getUserId());
        } else {
            permissionService.requireActiveChannelForMutation(existing.getChannelId());
        }
        Shop saved = shopService.update(id, shop);
        userService.refreshShopStats(saved.getUserId());
        if (ownerId != null && !ownerId.equals(saved.getUserId())) {
            userService.refreshShopStats(ownerId);
        }
        return ApiResponse.success(saved);
    }

    @PutMapping("/order")
    @Transactional
    public ApiResponse<List<ShopResponse>> reorder(@RequestBody ShopOrderRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (request == null || request.getShopIds() == null || request.getShopIds().isEmpty()) {
            return ApiResponse.error("排序列表不能为空");
        }
        try {
            User user = userService.findById(userId).orElse(null);
            List<Shop> shops = shopService.reorderUserPlatformShops(userId, request.getShopIds());
            return ApiResponse.success(shops.stream()
                    .map(shop -> ShopResponse.from(shop, user))
                    .toList());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
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
        User user = userService.refreshShopStats(userId);
        LocalDateTime expireAt = user.isSubscriptionActive() && user.getSubscriptionExpiresAt() != null
                ? user.getSubscriptionExpiresAt()
                : base.plusDays(DEFAULT_AUTH_DAYS);
        shop.setRemainingDays(DEFAULT_AUTH_DAYS);
        shop.setExpireAt(expireAt);
        shop.setAuthStartAt(shop.getAuthStartAt() == null ? now : shop.getAuthStartAt());
        shop.setAuthExpireAt(expireAt);
        shop.setCredentialVersion((shop.getCredentialVersion() == null ? 1 : shop.getCredentialVersion()) + 1);
        shop.setAuthorizationJti(randomHex(16));
        shop.setLastDeductedAt(now);
        Shop saved = shopService.update(shop.getId(), shop);
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
        User user = userService.refreshShopStats(userId);
        LocalDateTime expireAt = firstPresent(shop.getAuthExpireAt(), shop.getExpireAt());
        boolean legacyWithoutAuthorizationWindow = expireAt == null;
        if (legacyWithoutAuthorizationWindow) {
            expireAt = user.isSubscriptionActive() && user.getSubscriptionExpiresAt() != null
                    ? user.getSubscriptionExpiresAt()
                    : now.plusDays(DEFAULT_AUTH_DAYS);
            shop.setRemainingDays(DEFAULT_AUTH_DAYS);
        } else if (!expireAt.isAfter(now) && user.isSubscriptionActive() && user.getSubscriptionExpiresAt() != null) {
            expireAt = user.getSubscriptionExpiresAt();
            shop.setRemainingDays(DEFAULT_AUTH_DAYS);
            shop.setCredentialVersion((shop.getCredentialVersion() == null ? 1 : shop.getCredentialVersion()) + 1);
            shop.setAuthorizationJti(randomHex(16));
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
        String token = cloneAuthorizationTokenService.signToken(saved, user);
        return ApiResponse.success(CloneShopCreateResponse.from(
                saved,
                user,
                false,
                token,
                cloneAuthorizationTokenService.getPublicKeyId()
        ));
    }

    @PostMapping(value = "/{id}/login-state", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ShopResponse> uploadLoginState(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "profile", required = false) String profile,
            @RequestPart(value = "manifest", required = false) String manifest
    ) {
        Shop shop = shopService.findById(id)
                .filter(this::canAccessShop)
                .orElse(null);
        if (shop == null) {
            return ApiResponse.error("店铺不存在");
        }
        String normalizedProfile = normalize(profile);
        if (normalizedProfile == null) {
            return ApiResponse.error("缺少登录态档位");
        }
        if (!normalizedProfile.matches("[A-Za-z0-9._:-]{1,128}")) {
            return ApiResponse.error("登录态档位格式无效");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("登录态文件为空");
        }
        if (file.getSize() > MAX_LOGIN_STATE_BYTES) {
            return ApiResponse.error("登录态文件过大，请使用最小可用档位");
        }
        String normalizedManifest = manifest == null ? null : manifest.trim();
        if (normalizedManifest != null &&
                normalizedManifest.getBytes(StandardCharsets.UTF_8).length > MAX_LOGIN_STATE_MANIFEST_BYTES) {
            return ApiResponse.error("登录态清单过大");
        }
        String validationError = validateLoginStateManifest(shop, normalizedProfile, normalizedManifest);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        try {
            byte[] blob = file.getBytes();
            if (blob.length > MAX_LOGIN_STATE_BYTES) {
                return ApiResponse.error("登录态文件过大，请使用最小可用档位");
            }
            Shop saved = shopService.updateLoginState(
                    id,
                    normalizedProfile,
                    normalizedManifest,
                    blob,
                    sha256(blob),
                    extractArtifactCreatedAt(normalizedManifest)
            );
            return ApiResponse.success(ShopResponse.from(saved, userService.findById(saved.getUserId()).orElse(null)));
        } catch (Exception e) {
            return ApiResponse.error("保存登录态失败");
        }
    }

    @GetMapping("/{id}/authorization/open-url")
    public ApiResponse<Map<String, String>> openShopAuthorizationUrl(@PathVariable Long id) {
        permissionService.requireSuperAdmin();
        Shop shop = shopService.findById(id).orElse(null);
        if (shop == null) {
            return ApiResponse.error("店铺不存在");
        }
        User owner = userService.findById(shop.getUserId()).orElse(null);
        if (owner == null) {
            return ApiResponse.error("店铺所属用户不存在");
        }
        String authorizationUrl = resolvePlatformAuthorizationUrl(shop);
        if (authorizationUrl == null) {
            return ApiResponse.error("平台未配置授权地址");
        }
        try {
            JsonNode payload = requestBrowserControl(
                    "open",
                    owner,
                    shop,
                    authorizationUrl,
                    Map.of(
                            "width", "720",
                            "height", "900",
                            "renderWidth", "720",
                            "renderHeight", "900",
                            "renderScale", "1",
                            "scale", "1"
                    )
            );
            if (!payload.path("ok").asBoolean(false)) {
                return ApiResponse.error("远程授权窗口启动失败");
            }
            String streamUrl = fixedXpraClientUrl(zrStreamUrl);
            Shop saved = shopService.updateShopAuthorizationUrl(id, streamUrl);
            return ApiResponse.success(Map.of(
                    "url", streamUrl,
                    "shopAuthorizationUrl", firstNonBlank(saved.getShopAuthorizationUrl(), streamUrl),
                    "status", saved.getShopAuthorizationStatus()
            ));
        } catch (Exception e) {
            return ApiResponse.error("远程授权窗口启动失败");
        }
    }

    @PostMapping("/{id}/authorization/probe")
    public ApiResponse<ShopAuthorizationProbeResponse> probeShopAuthorization(@PathVariable Long id) {
        Shop shop = shopService.findById(id)
                .filter(this::canAccessShop)
                .orElse(null);
        if (shop == null) {
            return ApiResponse.error("店铺不存在");
        }
        User owner = userService.findById(shop.getUserId()).orElse(null);
        if (owner == null) {
            return ApiResponse.error("店铺所属用户不存在");
        }
        String authorizationUrl = resolvePlatformAuthorizationUrl(shop);
        if (authorizationUrl == null) {
            return saveAuthorizationProbeResult(id, "FAILED", "HIGH", errorSignals("missing_authorization_url"));
        }
        try {
            JsonNode payload = requestBrowserControl("probe", owner, shop, authorizationUrl, Map.of());
            String status = normalizeAuthorizationStatus(payload.path("status").asText("UNKNOWN"));
            if (!payload.path("ok").asBoolean(false) && !"UNAUTHORIZED".equals(status)) {
                status = "FAILED";
            }
            String confidence = normalizeProbeConfidence(payload.path("confidence").asText("LOW"));
            String signals = payload.has("signals")
                    ? OBJECT_MAPPER.writeValueAsString(payload.get("signals"))
                    : errorSignals("probe_without_signals");
            return saveAuthorizationProbeResult(id, status, confidence, signals);
        } catch (Exception e) {
            return saveAuthorizationProbeResult(id, "FAILED", "LOW", errorSignals("probe_request_failed"));
        }
    }

    @PostMapping("/{id}/authorization/failed")
    public ApiResponse<ShopAuthorizationProbeResponse> markShopAuthorizationFailed(@PathVariable Long id) {
        Shop shop = shopService.findById(id)
                .filter(this::canAccessShop)
                .orElse(null);
        if (shop == null) {
            return ApiResponse.error("店铺不存在");
        }
        return saveAuthorizationProbeResult(id, "FAILED", "HIGH", errorSignals("client_reported_failed"));
    }

    @GetMapping("/{id}/login-state")
    public ResponseEntity<byte[]> downloadLoginState(@PathVariable Long id) {
        Shop shop = shopService.findById(id)
                .filter(this::canAccessShop)
                .orElse(null);
        if (shop == null) {
            return ResponseEntity.status(404).build();
        }
        byte[] blob = shop.getLoginStateBlob();
        if (blob == null || blob.length == 0) {
            return ResponseEntity.noContent().build();
        }
        String profile = normalize(shop.getLoginStateProfile());
        String filename = "shop-" + id + "-login-state"
                + (profile == null ? "" : "-" + profile.replaceAll("[^A-Za-z0-9._-]", "_"))
                + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(blob.length)
                .header("X-Login-State-Profile", profile == null ? "" : profile)
                .header("X-Login-State-Sha256", shop.getLoginStateSha256() == null ? "" : shop.getLoginStateSha256())
                .header("X-Login-State-Artifact-Created-At", shop.getLoginStateArtifactCreatedAt() == null ? "" : shop.getLoginStateArtifactCreatedAt().toString())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(blob);
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

    private boolean isCurrentUserSuperAdmin() {
        try {
            return permissionService.currentPrincipal().isSuperAdmin();
        } catch (RuntimeException e) {
            return false;
        }
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

    private String validateLoginStateManifest(Shop shop, String profile, String manifest) {
        String shopPackageName = normalize(shop.getPackageName());
        if (shopPackageName == null) {
            return "店铺缺少应用包名，无法保存登录态";
        }
        if (!isAllowedLoginStateProfile(shopPackageName, profile)) {
            return "登录态档位与店铺平台不匹配";
        }
        if (manifest == null) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(manifest);
            if (root == null || !root.isObject()) {
                return "登录态清单格式无效";
            }
            JsonNode packageNode = root.get("packageName");
            if (packageNode != null && !packageNode.asText("").isBlank()
                    && !shopPackageName.equals(packageNode.asText().trim())) {
                return "登录态清单包名与店铺不匹配";
            }
            JsonNode profileNode = root.get("profileId");
            if (profileNode != null && !profileNode.asText("").isBlank()
                    && !profile.equals(profileNode.asText().trim())) {
                return "登录态清单档位与上传档位不匹配";
            }
            return null;
        } catch (Exception e) {
            return "登录态清单格式无效";
        }
    }

    private LocalDateTime extractArtifactCreatedAt(String manifest) {
        if (manifest == null) {
            return LocalDateTime.now();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(manifest);
            JsonNode millis = root.get("artifactCreatedAtEpochMillis");
            if (millis != null && millis.canConvertToLong()) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis.asLong()), ZoneOffset.UTC);
            }
            JsonNode iso = root.get("artifactCreatedAt");
            if (iso != null && !iso.asText("").isBlank()) {
                return LocalDateTime.parse(iso.asText().trim());
            }
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

    private ApiResponse<ShopAuthorizationProbeResponse> saveAuthorizationProbeResult(
            Long shopId,
            String status,
            String confidence,
            String signals
    ) {
        LocalDateTime checkedAt = LocalDateTime.now();
        Shop saved = shopService.updateShopAuthorizationStatus(shopId, status, signals, checkedAt);
        return ApiResponse.success(new ShopAuthorizationProbeResponse(
                saved.getShopAuthorizationStatus(),
                confidence,
                saved.getShopAuthorizationCheckedAt(),
                saved.getShopAuthorizationSignals()
        ));
    }

    private JsonNode requestBrowserControl(
            String action,
            User owner,
            Shop shop,
            String authorizationUrl,
            Map<String, String> extraParams
    ) throws Exception {
        String base = normalize(zrControlUrl);
        if (base == null) {
            throw new IllegalStateException("ZR control url is empty");
        }
        StringBuilder builder = new StringBuilder(base.replaceAll("/+$", ""))
                .append("/")
                .append(action)
                .append("?phone=")
                .append(urlEncode(firstNonBlank(owner.getPhone(), "unknown-phone")))
                .append("&shopId=")
                .append(urlEncode(authorizationProfileShopId(shop)))
                .append("&url=")
                .append(urlEncode(authorizationUrl));
        for (Map.Entry<String, String> entry : extraParams.entrySet()) {
            builder.append("&")
                    .append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(builder.toString()))
                .timeout(Duration.ofSeconds("open".equals(action) ? 35 : 30))
                .GET()
                .build();
        HttpResponse<String> response = authorizationHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode payload;
        try {
            payload = OBJECT_MAPPER.readTree(response.body());
        } catch (Exception e) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("ok", false);
            node.put("status", "FAILED");
            node.put("error", "invalid_browser_control_response");
            return node;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (payload instanceof ObjectNode objectNode) {
                objectNode.put("ok", false);
            } else {
                ObjectNode node = OBJECT_MAPPER.createObjectNode();
                node.put("ok", false);
                node.put("status", "FAILED");
                node.put("error", "browser_control_http_" + response.statusCode());
                return node;
            }
        }
        return payload;
    }

    private String resolvePlatformAuthorizationUrl(Shop shop) {
        String platform = normalize(shop.getPlatform());
        if (platform != null) {
            Optional<String> platformUrl = platformConfigRepository
                    .findByPlatformIdAndDeleted(platform, (byte) 0)
                    .map(PlatformConfig::getAuthorizationUrl)
                    .map(this::normalize);
            if (platformUrl.isPresent()) {
                return platformUrl.get();
            }
        }
        String packageName = normalize(shop.getPackageName());
        if (packageName != null) {
            return platformConfigRepository.findByDeletedOrderBySortOrderAscIdAsc((byte) 0).stream()
                    .filter(config -> packageName.equals(normalize(config.getPackageName())))
                    .map(PlatformConfig::getAuthorizationUrl)
                    .map(this::normalize)
                    .filter(value -> value != null)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String authorizationProfileShopId(Shop shop) {
        String shopId = normalize(shop.getShopId());
        if (shopId != null
                && !"-".equals(shopId)
                && !shopId.startsWith("NEW-")
                && !shopId.startsWith("phase13-")) {
            return shopId;
        }
        return "system-" + shop.getId();
    }

    private String normalizeAuthorizationStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return "UNAUTHORIZED";
        }
        return switch (normalized.toUpperCase()) {
            case "AUTHORIZING", "AUTHORIZED", "FAILED", "UNKNOWN", "UNAUTHORIZED" -> normalized.toUpperCase();
            default -> "UNKNOWN";
        };
    }

    private String normalizeProbeConfidence(String confidence) {
        String normalized = normalize(confidence);
        if (normalized == null) {
            return "LOW";
        }
        return switch (normalized.toUpperCase()) {
            case "HIGH", "MEDIUM", "LOW" -> normalized.toUpperCase();
            default -> "LOW";
        };
    }

    private String fixedXpraClientUrl(String streamUrl) {
        String base = firstNonBlank(streamUrl, "http://100.99.88.6:14500/");
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "autohide=true&touchaction=scroll&sound=false&video=false"
                + "&clipboard=false&printing=false&file_transfer=false";
    }

    private String errorSignals(String reason) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("reason", reason);
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"reason\":\"" + reason + "\"}";
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean isAllowedLoginStateProfile(String packageName, String profile) {
        return switch (packageName) {
            case "com.sankuai.meituan.meituanwaimaibusiness" ->
                    "meituan-waimai-cips-f".equals(profile) || "phase13-meituan-cips-f-20260611".equals(profile);
            case "com.jd.mrd.jingming" ->
                    "jd-jingming-prefs-d".equals(profile) || "phase13-jd-jingming-prefs-d-20260611".equals(profile);
            case "me.ele.napos" ->
                    "ele-napos-prefs-e-min".equals(profile) || "phase13-ele-napos-prefs-e-min-20260611".equals(profile);
            case "com.baidu.lbs.xinlingshou" ->
                    "ele-retail-prefs-e-min".equals(profile) || "phase13-ele-retail-prefs-e-min-20260613".equals(profile);
            case "com.sankuai.meituan.merchant" ->
                    "meituan-merchant-cips-e-min".equals(profile) || "phase13-meituan-merchant-cips-e-min-20260613".equals(profile);
            case "com.Hotel.EBooking" ->
                    "ctrip-ebooking-prefs-mmkv-e-min".equals(profile) || "phase13-ctrip-ebooking-prefs-mmkv-e-min-20260613".equals(profile);
            case "com.bytedance.ls.merchant" ->
                    "douyin-laike-account-keva-e-min".equals(profile) || "phase13-douyin-laike-account-keva-e-min-20260613".equals(profile);
            default -> false;
        };
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

    private String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
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
