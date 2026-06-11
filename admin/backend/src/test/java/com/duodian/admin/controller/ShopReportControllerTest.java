package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ShopReportRequest;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopReportControllerTest {

    private final ShopService shopService = mock(ShopService.class);
    private final UserService userService = mock(UserService.class);
    private final ShopReportController controller = new ShopReportController(shopService, userService);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void reportConvertsPendingShopWithoutDeductingAgain() {
        AuthContext.setUserId(1L);
        LocalDateTime deductedAt = LocalDateTime.now().minusMinutes(5);
        String cloneInstanceId = cloneIdForCode("server-random");
        Shop pendingShop = shop(20L, "NEW-abc", "新增店铺-[1]", cloneInstanceId);
        pendingShop.setLastDeductedAt(deductedAt);
        pendingShop.setCloneValidationCode("server-random");
        pendingShop.setCloneValidationHash(sha256(pendingShop.getCloneInstanceId() + ":server-random"));
        User user = new User();
        user.setId(1L);
        user.setComputeBalance(6);
        user.setShopCount(1);
        user.setPlatformCount(1);

        when(shopService.findByUserIdAndCloneInstanceId(1L, pendingShop.getCloneInstanceId())).thenReturn(Optional.of(pendingShop));
        when(shopService.findByUserIdAndShopIdAndPackageName(1L, "real-shop", "com.jd.pingou")).thenReturn(Optional.empty());
        when(shopService.update(eq(20L), any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(userService.refreshShopStats(1L)).thenReturn(user);

        ApiResponse<Map<String, Object>> response = controller.report(request("real-shop", "真实店铺", pendingShop.getCloneInstanceId()));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("deducted", false);
        assertThat(response.getData()).containsEntry("isNew", true);
        assertThat(pendingShop.getShopId()).isEqualTo("real-shop");
        assertThat(pendingShop.getIdentityVerified()).isTrue();
        assertThat(pendingShop.getIdentityVerifiedAt()).isNotNull();
        assertThat(pendingShop.getLastDeductedAt()).isEqualTo(deductedAt);
        verify(shopService, never()).create(any(Shop.class));
    }

    @Test
    void reportUpdatesExistingCloneEvenWhenRecognizedShopChanges() {
        AuthContext.setUserId(1L);
        String cloneInstanceId = cloneIdForCode("server-random");
        Shop oldShop = shop(10L, "old-shop", "旧店铺", cloneInstanceId);
        oldShop.setCloneValidationCode("server-random");
        oldShop.setCloneValidationHash(sha256(oldShop.getCloneInstanceId() + ":server-random"));
        User user = new User();
        user.setId(1L);
        user.setComputeBalance(6);
        user.setShopCount(1);
        user.setPlatformCount(1);

        when(shopService.findByUserIdAndCloneInstanceId(1L, oldShop.getCloneInstanceId())).thenReturn(Optional.of(oldShop));
        when(shopService.findByUserIdAndShopIdAndPackageName(1L, "new-shop", "com.jd.pingou")).thenReturn(Optional.empty());
        when(shopService.update(eq(10L), any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(userService.refreshShopStats(1L)).thenReturn(user);

        ApiResponse<Map<String, Object>> response = controller.report(request("new-shop", "新店铺", oldShop.getCloneInstanceId()));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("deducted", false);
        assertThat(response.getData()).containsEntry("switchedShop", false);
        assertThat(oldShop.getShopId()).isEqualTo("new-shop");
        assertThat(oldShop.getShopName()).isEqualTo("新店铺");
        assertThat(oldShop.getIdentityVerified()).isTrue();
        verify(shopService, never()).create(any(Shop.class));
    }

    @Test
    void reportWithoutVerifiedNameDoesNotOverwritePendingIdentity() {
        AuthContext.setUserId(1L);
        String cloneInstanceId = cloneIdForCode("server-random");
        Shop pendingShop = shop(20L, "NEW-abc", "新增店铺-[1]", cloneInstanceId);
        pendingShop.setCloneValidationCode("server-random");
        pendingShop.setCloneValidationHash(sha256(pendingShop.getCloneInstanceId() + ":server-random"));
        User user = new User();
        user.setId(1L);
        user.setComputeBalance(6);
        user.setShopCount(1);
        user.setPlatformCount(1);

        when(shopService.findByUserIdAndCloneInstanceId(1L, pendingShop.getCloneInstanceId())).thenReturn(Optional.of(pendingShop));
        when(shopService.update(eq(20L), any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(userService.refreshShopStats(1L)).thenReturn(user);

        ApiResponse<Map<String, Object>> response = controller.report(request("real-shop", "新增店铺-[1]", pendingShop.getCloneInstanceId()));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("isNew", false);
        assertThat(pendingShop.getShopId()).isEqualTo("NEW-abc");
        assertThat(pendingShop.getShopName()).isEqualTo("新增店铺-[1]");
        assertThat(pendingShop.getIdentityVerified()).isFalse();
        assertThat(pendingShop.getIdentityVerifiedAt()).isNull();
        verify(shopService, never()).findByUserIdAndShopIdAndPackageName(1L, "real-shop", "com.jd.pingou");
    }

    @Test
    void reportRejectsWithoutExistingCloneOrPendingShop() {
        AuthContext.setUserId(1L);

        when(shopService.findByUserIdAndCloneInstanceId(1L, "CLN-missing")).thenReturn(Optional.empty());
        when(shopService.findByUserIdAndShopIdAndPackageName(1L, "real-shop", "com.jd.pingou")).thenReturn(Optional.empty());
        when(shopService.findPendingByUserPackage(1L, "com.jd.pingou")).thenReturn(Optional.empty());

        ApiResponse<Map<String, Object>> response = controller.report(request("real-shop", "真实店铺", "CLN-missing"));

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("待登录店铺不存在，请先添加店铺卡片");
        verify(shopService, never()).update(eq(20L), any(Shop.class));
    }

    @Test
    void reportRejectsWhenServerValidationCodeDoesNotMatchCloneId() {
        AuthContext.setUserId(1L);
        Shop pendingShop = shop(20L, "NEW-abc", "新增店铺-[1]", cloneIdForCode("server-random"));
        pendingShop.setCloneValidationCode("server-random");
        pendingShop.setCloneValidationHash("wrong-hash");

        when(shopService.findByUserIdAndCloneInstanceId(1L, pendingShop.getCloneInstanceId())).thenReturn(Optional.of(pendingShop));

        ApiResponse<Map<String, Object>> response = controller.report(request("real-shop", "真实店铺", pendingShop.getCloneInstanceId()));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getMessage()).isEqualTo("店铺标识校验失败");
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

    private String cloneIdForCode(String validationCode) {
        return "CLN1-13800000001-com.jd.pingou-N1-U3-R" + sha256(validationCode).substring(0, 8);
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
