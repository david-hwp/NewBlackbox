package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.config.CloneAuthorizationProperties;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.CloneShopCreateRequest;
import com.duodian.admin.controller.dto.CloneShopCreateResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.ShopRenewRequest;
import com.duodian.admin.controller.dto.ShopRenewResponse;
import com.duodian.admin.controller.dto.ShopAuthTokenRequest;
import com.duodian.admin.controller.dto.ShopResponse;
import com.duodian.admin.entity.ComputeDeduction;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.CloneAuthorizationTokenService;
import com.duodian.admin.service.ComputeService;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopControllerTest {

    private final ShopService shopService = mock(ShopService.class);
    private final UserService userService = mock(UserService.class);
    private final ComputeService computeService = mock(ComputeService.class);
    private final CloneAuthorizationTokenService tokenService =
            new CloneAuthorizationTokenService(testCloneAuthProperties());
    private final ShopController controller = new ShopController(shopService, userService, computeService, tokenService);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void listRestrictsNormalUserToOwnShopsEvenWhenUserIdParamIsProvided() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        Shop ownShop = shop(10L, 1L, "own");
        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findByUserId(1L)).thenReturn(List.of(ownShop));

        ApiResponse<?> response = controller.list(2L, null, null, null, null, null, null, null);

        assertThat(response.getCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<ShopResponse> data = (List<ShopResponse>) response.getData();
        assertThat(data).extracting(ShopResponse::getUserId).containsExactly(1L);
        verify(shopService).findByUserId(1L);
        verify(shopService, never()).findByUserId(2L);
        verify(shopService, never()).findAll();
    }

    @Test
    void myShopsReturnsRemainingDaysCalculatedFromExpiration() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        Shop shop = shop(10L, 1L, "yesterday");
        shop.setRemainingDays(30);
        shop.setExpireAt(LocalDateTime.now().plusDays(29));
        shop.setAuthExpireAt(shop.getExpireAt());
        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findByUserId(1L)).thenReturn(List.of(shop));

        ApiResponse<List<ShopResponse>> response = controller.myShops();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).extracting(ShopResponse::getRemainingDays).containsExactly(29);
    }

    @Test
    void listSupportsPagedAdminFilters() {
        AuthContext.setUserId(1L);
        User admin = user(1L, "ADMIN");
        Shop first = shop(10L, 2L, "京东店");
        first.setPackageName("com.jd.mrd.jingming");
        when(userService.findById(1L)).thenReturn(Optional.of(admin));
        when(userService.findById(2L)).thenReturn(Optional.of(user(2L, "USER")));
        when(shopService.search(
                eq(null),
                eq(null),
                eq("jd"),
                eq("138"),
                eq("user"),
                eq("京东"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 20), 1));

        ApiResponse<?> response = controller.list(null, null, "jd", "138", "user", "京东", 1, 20);

        assertThat(response.getCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        PagedResponse<ShopResponse> page = (PagedResponse<ShopResponse>) response.getData();
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).extracting(ShopResponse::getShopName).containsExactly("京东店");
    }

    @Test
    void createRejectsNormalUserToAvoidBypassingCloneDeduction() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));

        ApiResponse<Shop> response = controller.create(shop(20L, 1L, "manual"));

        assertThat(response.getCode()).isEqualTo(403);
        verify(shopService, never()).create(argThat(shop -> true));
    }

    @Test
    void updateRejectsManualShopIdentityChange() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        Shop existing = shop(10L, 1L, "真实店铺");
        existing.setShopId("100001");
        Shop request = shop(10L, 1L, "手填店铺");
        request.setShopId("999999");

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(10L)).thenReturn(Optional.of(existing));

        ApiResponse<Shop> response = controller.update(10L, request);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("店铺ID和店铺名称只能由引擎识别更新");
        verify(shopService, never()).update(eq(10L), any(Shop.class));
    }

    @Test
    void legacyPendingDeductIsBlocked() {
        AuthContext.setUserId(1L);

        ApiResponse<?> response = controller.createPendingWithDeduction(null);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("请使用店铺创建接口新增店铺");
        verify(computeService, never()).deductComputeForClone(any(), any(), any(), any());
    }

    @Test
    void cloneCreateCreatesNewTaggedShopAndReturnsAuthorizationToken() throws Exception {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        normalUser.setComputeBalance(7);
        normalUser.setShopCount(1);
        normalUser.setPlatformCount(1);
        CloneShopCreateRequest request = createRequest("op-create-1", 3);

        when(computeService.lockActiveUser(1L)).thenReturn(Optional.of(normalUser));
        when(computeService.findExistingCloneCreateOperation(1L, "op-create-1")).thenReturn(Optional.empty());
        when(shopService.countByUserIdAndPackageName(1L, "com.jd.mrd.jingming")).thenReturn(0L);
        when(shopService.findByCloneInstanceId(argThat(id -> id != null && id.startsWith("CLN1-13800000001-com.jd.mrd.jingming-N1-U3-R"))))
                .thenReturn(Optional.empty());
        when(computeService.deductComputeForCloneCreate(
                eq(1L),
                argThat(id -> id != null && id.startsWith("CLN1-13800000001-com.jd.mrd.jingming-N1-U3-R")),
                eq("op-create-1"),
                eq("jd"),
                eq("新增店铺-[1]")
        )).thenReturn(new ComputeService.DeductionResult(true, true, 88L));
        when(shopService.create(argThat(shop ->
                shop.getShopId().startsWith("NEW-")
                        && "新增店铺-[1]".equals(shop.getShopName())
                        && shop.getCloneInstanceId().startsWith("CLN1-13800000001-com.jd.mrd.jingming-N1-U3-R")
                        && shop.getCloneSequence().equals(1)
                        && shop.getLocalVirtualUserId().equals(3)
                        && shop.getCloneValidationCode() != null
                        && shop.getCloneValidationHash() != null
                        && shop.getCredentialVersion().equals(1)
                        && shop.getAuthorizationJti() != null
                        && shop.getAuthExpireAt() != null
        ))).thenAnswer(invocation -> {
            Shop saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(userService.refreshShopStats(1L)).thenReturn(normalUser);

        ApiResponse<CloneShopCreateResponse> response = controller.createCloneShop(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getDeducted()).isTrue();
        assertThat(response.getData().getShop().getShopId()).startsWith("NEW-");
        assertThat(response.getData().getShop().getCloneInstanceId())
                .startsWith("CLN1-13800000001-com.jd.mrd.jingming-N1-U3-R");
        assertThat(response.getData().getAuthorizationToken()).contains(".");
        assertThat(response.getData().getAuthorizationToken()).doesNotContain("新增店铺-[1]");
        assertThat(response.getData().getAuthorizationToken()).doesNotContain(response.getData().getShop().getShopId());
        assertThat(Shop.class.getMethod("getCloneValidationCode").isAnnotationPresent(JsonIgnore.class)).isTrue();
        assertThat(Shop.class.getMethod("getCloneValidationHash").isAnnotationPresent(JsonIgnore.class)).isTrue();
    }

    @Test
    void cloneCreateAllowsAnotherPendingNewShopForSamePackage() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        normalUser.setComputeBalance(6);
        normalUser.setShopCount(2);
        normalUser.setPlatformCount(1);
        CloneShopCreateRequest request = createRequest("op-create-2", 4);

        when(computeService.findExistingCloneCreateOperation(1L, "op-create-2")).thenReturn(Optional.empty());
        when(computeService.lockActiveUser(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.countByUserIdAndPackageName(1L, "com.jd.mrd.jingming")).thenReturn(1L);
        when(shopService.findByCloneInstanceId(argThat(id -> id != null && id.startsWith("CLN1-13800000001-com.jd.mrd.jingming-N2-U4-R"))))
                .thenReturn(Optional.empty());
        when(computeService.deductComputeForCloneCreate(
                eq(1L),
                argThat(id -> id != null && id.startsWith("CLN1-13800000001-com.jd.mrd.jingming-N2-U4-R")),
                eq("op-create-2"),
                eq("jd"),
                eq("新增店铺-[2]")
        )).thenReturn(new ComputeService.DeductionResult(true, true, 89L));
        when(shopService.create(argThat(shop ->
                "新增店铺-[2]".equals(shop.getShopName())
                        && shop.getCloneInstanceId().startsWith("CLN1-13800000001-com.jd.mrd.jingming-N2-U4-R")
                        && shop.getCloneSequence().equals(2)
                        && shop.getLocalVirtualUserId().equals(4)
        ))).thenAnswer(invocation -> {
            Shop saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(userService.refreshShopStats(1L)).thenReturn(normalUser);

        ApiResponse<CloneShopCreateResponse> response = controller.createCloneShop(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getDeducted()).isTrue();
        assertThat(response.getData().getShop().getShopName()).isEqualTo("新增店铺-[2]");
        assertThat(response.getData().getShop().getLocalVirtualUserId()).isEqualTo(4);
    }

    @Test
    void cloneCreateDuplicateOperationReturnsExistingShopWithoutDeductingAgain() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        normalUser.setComputeBalance(7);
        Shop existingShop = shop(99L, 1L, "新增店铺-[1]");
        existingShop.setPackageName("com.jd.mrd.jingming");
        existingShop.setCloneInstanceId("CLN1-13800000001-com.jd.mrd.jingming-N1-U3-Rabcd1234");
        existingShop.setLocalVirtualUserId(3);
        existingShop.setAuthStartAt(LocalDateTime.now().minusMinutes(1));
        existingShop.setAuthExpireAt(LocalDateTime.now().plusDays(30));
        existingShop.setAuthorizationJti("jti");
        ComputeDeduction deduction = new ComputeDeduction();
        deduction.setCloneInstanceId(existingShop.getCloneInstanceId());

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(computeService.findExistingCloneCreateOperation(1L, "op-create-1")).thenReturn(Optional.of(deduction));
        when(shopService.findByUserIdAndCloneInstanceId(1L, existingShop.getCloneInstanceId())).thenReturn(Optional.of(existingShop));
        when(userService.refreshShopStats(1L)).thenReturn(normalUser);

        ApiResponse<CloneShopCreateResponse> response = controller.createCloneShop(createRequest("op-create-1", 3));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getDeducted()).isFalse();
        assertThat(response.getData().getShop().getId()).isEqualTo(99L);
        verify(computeService, never()).deductComputeForCloneCreate(any(), any(), any(), any(), any());
    }

    @Test
    void renewDeductsByCloneAndReturnsNewAuthorizationToken() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        normalUser.setComputeBalance(8);
        normalUser.setShopCount(1);
        normalUser.setPlatformCount(1);
        Shop expiredShop = shop(10L, 1L, "expired");
        expiredShop.setCloneInstanceId("CLN1-13800000001-com.jd.mrd.jingming-N1-U3-Rabcd1234");
        expiredShop.setPackageName("com.jd.mrd.jingming");
        expiredShop.setLocalVirtualUserId(3);
        expiredShop.setAuthStartAt(LocalDateTime.now().minusDays(31));
        expiredShop.setAuthExpireAt(LocalDateTime.now().minusDays(1));
        expiredShop.setAuthorizationJti("old");
        expiredShop.setRemainingDays(0);
        ShopRenewRequest request = new ShopRenewRequest();
        request.setOperationKey("op-renew-1");

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(10L)).thenReturn(Optional.of(expiredShop));
        when(computeService.deductComputeForCloneRenew(
                1L,
                expiredShop.getCloneInstanceId(),
                "op-renew-1",
                "jd",
                "expired"
        )).thenReturn(new ComputeService.DeductionResult(true, true, 89L));
        when(shopService.update(10L, expiredShop)).thenReturn(expiredShop);
        when(userService.refreshShopStats(1L)).thenReturn(normalUser);

        ApiResponse<ShopRenewResponse> response = controller.renew(10L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getShop().getRemainingDays()).isEqualTo(30);
        assertThat(response.getData().getAuthorizationToken()).contains(".");
        assertThat(expiredShop.getAuthExpireAt()).isAfter(LocalDateTime.now());
        assertThat(expiredShop.getCredentialVersion()).isEqualTo(2);
        verify(computeService).deductComputeForCloneRenew(
                1L,
                expiredShop.getCloneInstanceId(),
                "op-renew-1",
                "jd",
                "expired"
        );
    }

    @Test
    void authTokenBackfillsLegacyShopWithoutDeducting() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        normalUser.setComputeBalance(7);
        Shop legacyShop = shop(11L, 1L, "legacy");
        legacyShop.setCloneInstanceId("clone-legacy");
        legacyShop.setPackageName("com.jd.mrd.jingming");
        legacyShop.setLocalVirtualUserId(null);
        legacyShop.setAuthStartAt(null);
        legacyShop.setAuthExpireAt(null);
        legacyShop.setExpireAt(null);
        ShopAuthTokenRequest request = new ShopAuthTokenRequest();
        request.setLocalVirtualUserId(6);
        request.setPackageName("com.jd.mrd.jingming");

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(11L)).thenReturn(Optional.of(legacyShop));
        when(shopService.update(11L, legacyShop)).thenReturn(legacyShop);
        when(userService.refreshShopStats(1L)).thenReturn(normalUser);

        ApiResponse<CloneShopCreateResponse> response = controller.issueAuthorizationToken(11L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getDeducted()).isFalse();
        assertThat(legacyShop.getLocalVirtualUserId()).isEqualTo(6);
        assertThat(legacyShop.getAuthExpireAt()).isAfter(LocalDateTime.now());
        assertThat(legacyShop.getExpireAt()).isEqualTo(legacyShop.getAuthExpireAt());
        assertThat(response.getData().getAuthorizationToken()).contains(".");
        verify(computeService, never()).deductComputeForCloneRenew(any(), any(), any(), any(), any());
        verify(computeService, never()).deductComputeForCloneCreate(any(), any(), any(), any(), any());
    }

    @Test
    void activeSubscriptionCanIssueTokenForExpiredShop() {
        AuthContext.setUserId(1L);
        User subscriber = user(1L, "USER");
        subscriber.setSubscriptionPlan("MONTHLY");
        subscriber.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(12));
        Shop expiredShop = shop(15L, 1L, "expired-sub");
        expiredShop.setCloneInstanceId("clone-sub");
        expiredShop.setPackageName("com.jd.mrd.jingming");
        expiredShop.setLocalVirtualUserId(7);
        expiredShop.setAuthStartAt(LocalDateTime.now().minusDays(40));
        expiredShop.setAuthExpireAt(LocalDateTime.now().minusDays(1));
        expiredShop.setExpireAt(expiredShop.getAuthExpireAt());
        expiredShop.setCredentialVersion(1);
        expiredShop.setAuthorizationJti("old");
        ShopAuthTokenRequest request = new ShopAuthTokenRequest();
        request.setLocalVirtualUserId(7);
        request.setPackageName("com.jd.mrd.jingming");

        when(userService.findById(1L)).thenReturn(Optional.of(subscriber));
        when(userService.refreshShopStats(1L)).thenReturn(subscriber);
        when(shopService.findById(15L)).thenReturn(Optional.of(expiredShop));
        when(shopService.update(15L, expiredShop)).thenReturn(expiredShop);

        ApiResponse<CloneShopCreateResponse> response = controller.issueAuthorizationToken(15L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(expiredShop.getAuthExpireAt()).isEqualTo(subscriber.getSubscriptionExpiresAt());
        assertThat(expiredShop.getCredentialVersion()).isEqualTo(2);
        assertThat(response.getData().getAuthorizationToken()).contains(".");
    }

    @Test
    void uploadLoginStateStoresBlobMetadataWithoutReturningBlob() throws Exception {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        Shop shop = shop(12L, 1L, "login-state");
        shop.setPackageName("com.jd.mrd.jingming");
        byte[] payload = "zip-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "login-state.zip",
                "application/zip",
                payload
        );

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(12L)).thenReturn(Optional.of(shop));
        when(shopService.updateLoginState(
                eq(12L),
                eq("jd-jingming-prefs-d"),
                eq("{\"packageName\":\"com.jd.mrd.jingming\",\"profileId\":\"jd-jingming-prefs-d\",\"files\":1}"),
                argThat(bytes -> Arrays.equals(bytes, payload)),
                argThat(hash -> hash != null && hash.matches("[0-9a-f]{64}"))
        )).thenAnswer(invocation -> {
            Shop saved = shop;
            saved.setLoginStateProfile(invocation.getArgument(1));
            saved.setLoginStateManifest(invocation.getArgument(2));
            saved.setLoginStateBlob(invocation.getArgument(3));
            saved.setLoginStateSha256(invocation.getArgument(4));
            saved.setLoginStateSize((long) payload.length);
            saved.setLoginStateUpdatedAt(LocalDateTime.now());
            return saved;
        });

        ApiResponse<ShopResponse> response = controller.uploadLoginState(
                12L,
                file,
                "jd-jingming-prefs-d",
                "{\"packageName\":\"com.jd.mrd.jingming\",\"profileId\":\"jd-jingming-prefs-d\",\"files\":1}"
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getHasLoginState()).isTrue();
        assertThat(response.getData().getLoginStateProfile()).isEqualTo("jd-jingming-prefs-d");
        assertThat(response.getData().getLoginStateSize()).isEqualTo(payload.length);
        assertThat(response.getData().getLoginStateSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void uploadLoginStateRejectsManifestPackageMismatch() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        Shop shop = shop(13L, 1L, "login-state-mismatch");
        shop.setPackageName("com.jd.mrd.jingming");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "login-state.zip",
                "application/zip",
                "zip-bytes".getBytes()
        );

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(13L)).thenReturn(Optional.of(shop));

        ApiResponse<ShopResponse> response = controller.uploadLoginState(
                13L,
                file,
                "jd-jingming-prefs-d",
                "{\"packageName\":\"me.ele.napos\",\"profileId\":\"jd-jingming-prefs-d\"}"
        );

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("包名");
        verify(shopService, never()).updateLoginState(any(), any(), any(), any(), any());
    }

    @Test
    void uploadLoginStateRejectsOversizedCloneData() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        Shop shop = shop(13L, 1L, "oversized");
        shop.setPackageName("me.ele.napos");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "full-clone.zip",
                "application/zip",
                new byte[2 * 1024 * 1024 + 1]
        );

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(13L)).thenReturn(Optional.of(shop));

        ApiResponse<ShopResponse> response = controller.uploadLoginState(13L, file, "ele-napos-prefs-e-min", null);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("过大");
        verify(shopService, never()).updateLoginState(any(), any(), any(), any(), any());
    }

    @Test
    void downloadLoginStateReturnsStoredBytesForOwner() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        Shop shop = shop(14L, 1L, "download");
        shop.setLoginStateProfile("phase13-ele-e");
        shop.setLoginStateBlob("zip".getBytes());
        shop.setLoginStateSize(3L);
        shop.setLoginStateSha256("abc");

        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(14L)).thenReturn(Optional.of(shop));

        ResponseEntity<byte[]> response = controller.downloadLoginState(14L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("X-Login-State-Profile")).isEqualTo("phase13-ele-e");
        assertThat(response.getBody()).isEqualTo("zip".getBytes());
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setPhone("1380000000" + id);
        user.setRole(role);
        return user;
    }

    private Shop shop(Long id, Long userId, String shopName) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setUserId(userId);
        shop.setShopName(shopName);
        shop.setShopId("shop-" + id);
        shop.setPlatform("jd");
        shop.setPlatformName("京东秒送");
        shop.setRemainingDays(30);
        shop.setAutoRenew(false);
        return shop;
    }

    private CloneShopCreateRequest createRequest(String operationKey, int localVirtualUserId) {
        CloneShopCreateRequest request = new CloneShopCreateRequest();
        request.setPlatform("jd");
        request.setPlatformName("京东秒送");
        request.setPackageName("com.jd.mrd.jingming");
        request.setLocalVirtualUserId(localVirtualUserId);
        request.setOperationKey(operationKey);
        return request;
    }

    private CloneAuthorizationProperties testCloneAuthProperties() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            CloneAuthorizationProperties properties = new CloneAuthorizationProperties();
            properties.setPrivateKey(Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded()));
            return properties;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create test clone auth key", e);
        }
    }
}
