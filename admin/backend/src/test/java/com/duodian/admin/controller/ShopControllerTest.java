package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ShopResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ShopService;
import com.duodian.admin.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopControllerTest {

    private final ShopService shopService = mock(ShopService.class);
    private final UserService userService = mock(UserService.class);
    private final ShopController controller = new ShopController(shopService, userService);

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
}
