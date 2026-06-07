package com.duodian.admin.service;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.repository.ShopRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopServiceTest {
    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final ShopService shopService = new ShopService(shopRepository);

    @Test
    void refreshRemainingDaysUpdatesStaleStoredValueFromExpiration() {
        Shop stale = shop(10L, 30, LocalDateTime.now().plusDays(29));
        when(shopRepository.findActiveShopsWithExpiration((byte) 0)).thenReturn(List.of(stale));

        int updated = shopService.refreshRemainingDays();

        assertThat(updated).isEqualTo(1);
        verify(shopRepository).save(argThat(shop ->
                shop.getId().equals(10L) && shop.getRemainingDays().equals(29)
        ));
    }

    private Shop shop(Long id, int remainingDays, LocalDateTime expireAt) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setUserId(1L);
        shop.setShopName("店铺");
        shop.setShopId("shop-" + id);
        shop.setPlatform("jd");
        shop.setRemainingDays(remainingDays);
        shop.setExpireAt(expireAt);
        shop.setAuthExpireAt(expireAt);
        return shop;
    }
}
