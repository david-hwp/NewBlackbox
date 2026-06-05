package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ShopReportRequest;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ComputeService;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopReportControllerTest {

    private final ShopService shopService = mock(ShopService.class);
    private final ComputeService computeService = mock(ComputeService.class);
    private final UserService userService = mock(UserService.class);
    private final ShopReportController controller = new ShopReportController(shopService, computeService, userService);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void reportCreatesNewShopWhenCloneRecognizesDifferentRealShop() {
        AuthContext.setUserId(1L);
        Shop oldShop = shop(10L, "old-shop", "旧店铺", "clone-original");
        User user = new User();
        user.setId(1L);
        user.setComputeBalance(6);
        user.setShopCount(2);
        user.setPlatformCount(1);

        when(shopService.findByUserIdAndShopIdAndPlatform(1L, "new-shop", "jd")).thenReturn(Optional.empty());
        when(shopService.findByUserIdAndCloneInstanceId(1L, "clone-original")).thenReturn(Optional.of(oldShop));
        when(shopService.findByUserIdAndCloneInstanceId(eq(1L), argThat(id -> id != null && id.startsWith("clone-switch-"))))
                .thenReturn(Optional.empty());
        when(computeService.deductCompute(1L, "new-shop", "新店铺", "jd")).thenReturn(true);
        when(userService.refreshShopStats(1L)).thenReturn(user);

        ApiResponse<Map<String, Object>> response = controller.report(request("new-shop", "新店铺", "clone-original"));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("deducted", true);
        assertThat(response.getData()).containsEntry("isNew", true);
        assertThat(response.getData()).containsEntry("switchedShop", true);
        assertThat(response.getData().get("message")).isEqualTo("检测到店铺切换，已创建新店铺并扣划算力");
        verify(shopService).create(argThat(shop ->
                shop.getUserId().equals(1L)
                        && shop.getShopId().equals("new-shop")
                        && shop.getCloneInstanceId().startsWith("clone-switch-")
        ));
    }

    @Test
    void reportConvertsPreDeductedPendingSwitchShopWithoutDeductingAgain() {
        AuthContext.setUserId(1L);
        LocalDateTime deductedAt = LocalDateTime.now().minusMinutes(5);
        Shop pendingShop = shop(
                20L,
                pendingSwitchShopId("real-shop"),
                "检测店铺",
                "clone-new"
        );
        pendingShop.setLastDeductedAt(deductedAt);
        User user = new User();
        user.setId(1L);
        user.setComputeBalance(6);
        user.setShopCount(1);
        user.setPlatformCount(1);

        when(shopService.findByUserIdAndShopIdAndPlatform(1L, "real-shop", "jd")).thenReturn(Optional.empty());
        when(shopService.findByUserIdAndCloneInstanceId(1L, "clone-new")).thenReturn(Optional.of(pendingShop));
        when(shopService.update(eq(20L), any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(userService.refreshShopStats(1L)).thenReturn(user);

        ApiResponse<Map<String, Object>> response = controller.report(request("real-shop", "真实店铺", "clone-new"));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("deducted", false);
        assertThat(response.getData()).containsEntry("isNew", true);
        assertThat(pendingShop.getShopId()).isEqualTo("real-shop");
        assertThat(pendingShop.getLastDeductedAt()).isEqualTo(deductedAt);
        verify(computeService, never()).deductCompute(1L, "real-shop", "真实店铺", "jd");
    }

    @Test
    void reportRejectsPendingSwitchShopWhenUserRecognizesWrongShop() {
        AuthContext.setUserId(1L);
        Shop pendingShop = shop(
                20L,
                pendingSwitchShopId("expected-shop"),
                "检测店铺",
                "clone-new"
        );
        pendingShop.setLastDeductedAt(LocalDateTime.now().minusMinutes(5));

        when(shopService.findByUserIdAndShopIdAndPlatform(1L, "wrong-shop", "jd")).thenReturn(Optional.empty());
        when(shopService.findByUserIdAndCloneInstanceId(1L, "clone-new")).thenReturn(Optional.of(pendingShop));

        ApiResponse<Map<String, Object>> response = controller.report(request("wrong-shop", "错误店铺", "clone-new"));

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("请在新分身中切换到对应店铺后重试");
        verify(computeService, never()).deductCompute(1L, "wrong-shop", "错误店铺", "jd");
        verify(shopService, never()).update(eq(20L), any(Shop.class));
    }

    private ShopReportRequest request(String shopId, String shopName, String cloneInstanceId) {
        ShopReportRequest request = new ShopReportRequest();
        request.setShopId(shopId);
        request.setShopName(shopName);
        request.setPlatform("jd");
        request.setPlatformName("京东秒送");
        request.setPackageName("com.jd.pingou");
        request.setCloneInstanceId(cloneInstanceId);
        request.setRemainingDays(30);
        request.setAutoRenew(false);
        return request;
    }

    private Shop shop(Long id, String shopId, String shopName, String cloneInstanceId) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setUserId(1L);
        shop.setShopId(shopId);
        shop.setShopName(shopName);
        shop.setPlatform("jd");
        shop.setPlatformName("京东秒送");
        shop.setPackageName("com.jd.pingou");
        shop.setCloneInstanceId(cloneInstanceId);
        shop.setRemainingDays(30);
        shop.setAutoRenew(false);
        return shop;
    }

    private String pendingSwitchShopId(String shopId) {
        return "NEW-SWITCH-jd-" + sha256(shopId).substring(0, 16);
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
