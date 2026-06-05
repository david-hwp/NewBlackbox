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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private final ShopController controller = new ShopController(shopService, userService, computeService);

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

        ApiResponse<List<ShopResponse>> response = controller.list(2L, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).extracting(ShopResponse::getUserId).containsExactly(1L);
        verify(shopService).findByUserId(1L);
        verify(shopService, never()).findByUserId(2L);
        verify(shopService, never()).findAll();
    }

    @Test
    void listAllowsAdminToViewAllShops() {
        AuthContext.setUserId(1L);
        User admin = user(1L, "ADMIN");
        Shop first = shop(10L, 1L, "first");
        Shop second = shop(11L, 2L, "second");
        when(userService.findById(1L)).thenReturn(Optional.of(admin));
        when(shopService.findAll()).thenReturn(List.of(first, second));

        ApiResponse<List<ShopResponse>> response = controller.list(null, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).extracting(ShopResponse::getUserId).containsExactly(1L, 2L);
        verify(shopService).findAll();
    }

    @Test
    void renewDeductsComputeAndExtendsOwnExpiredShop() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        normalUser.setComputeBalance(8);
        normalUser.setShopCount(1);
        normalUser.setPlatformCount(1);
        Shop expiredShop = shop(10L, 1L, "expired");
        expiredShop.setRemainingDays(0);
        when(userService.findById(1L)).thenReturn(Optional.of(normalUser));
        when(shopService.findById(10L)).thenReturn(Optional.of(expiredShop));
        when(computeService.deductComputeForRenewal(1L, "shop-10", "expired", "jd")).thenReturn(true);
        when(shopService.update(10L, expiredShop)).thenReturn(expiredShop);
        when(userService.refreshShopStats(1L)).thenReturn(normalUser);

        ApiResponse<ShopRenewResponse> response = controller.renew(10L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getShop().getRemainingDays()).isEqualTo(30);
        assertThat(response.getData().getBalance()).isEqualTo(8);
        assertThat(expiredShop.getExpireAt()).isNotNull();
        verify(computeService).deductComputeForRenewal(1L, "shop-10", "expired", "jd");
    }

    @Test
    void createPendingWithDeductionCreatesNewTaggedShopAndDeductsOnce() {
        AuthContext.setUserId(1L);
        User normalUser = user(1L, "USER");
        normalUser.setComputeBalance(7);
        normalUser.setShopCount(1);
        normalUser.setPlatformCount(1);
        ShopReportRequest request = reportRequest("detected-shop", "检测店铺");

        when(shopService.findByUserIdAndShopIdAndPlatform(1L, "detected-shop", "jd")).thenReturn(Optional.empty());
        when(shopService.findByUserIdAndShopIdAndPlatform(
                eq(1L),
                argThat(shopId -> shopId != null && shopId.startsWith("NEW-SWITCH-jd-")),
                eq("jd"))
        ).thenReturn(Optional.empty());
        when(computeService.deductCompute(1L, "detected-shop", "检测店铺", "jd")).thenReturn(true);
        when(shopService.create(argThat(shop ->
                shop.getShopId().startsWith("NEW-SWITCH-jd-")
                        && shop.getCloneInstanceId() == null
                        && shop.getLastDeductedAt() != null
        ))).thenAnswer(invocation -> {
            Shop saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(userService.refreshShopStats(1L)).thenReturn(normalUser);

        ApiResponse<PendingShopDeductResponse> response = controller.createPendingWithDeduction(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getDeducted()).isTrue();
        assertThat(response.getData().getShop().getShopId()).startsWith("NEW-SWITCH-jd-");
        assertThat(response.getData().getShop().getCloneInstanceId()).isNull();
        assertThat(response.getData().getBalance()).isEqualTo(7);
        verify(computeService).deductCompute(1L, "detected-shop", "检测店铺", "jd");
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

    private ShopReportRequest reportRequest(String shopId, String shopName) {
        ShopReportRequest request = new ShopReportRequest();
        request.setShopId(shopId);
        request.setShopName(shopName);
        request.setPlatform("jd");
        request.setPlatformName("京东秒送");
        request.setPackageName("com.jd.mrd.jingming");
        request.setRemainingDays(30);
        request.setAutoRenew(false);
        return request;
    }
}
