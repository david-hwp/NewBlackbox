package com.duodian.admin.service;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.repository.ShopRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Test
    void updateClearsNewTagWhenPendingShopNameChanges() {
        Shop existing = shop(11L, 30, LocalDateTime.now().plusDays(30));
        existing.setShopName("新增店铺-[1]");
        existing.setShopId("NEW-abc");
        Shop request = shop(11L, 30, LocalDateTime.now().plusDays(30));
        request.setShopName("手动命名店铺");
        request.setShopId("NEW-abc");
        when(shopRepository.findByIdAndDeleted(11L, (byte) 0)).thenReturn(Optional.of(existing));
        when(shopRepository.save(existing)).thenReturn(existing);

        Shop updated = shopService.update(11L, request);

        assertThat(updated.getShopName()).isEqualTo("手动命名店铺");
        assertThat(updated.getShopId()).isEqualTo("-");
    }

    @Test
    void updateLoginStateIgnoresOlderArtifact() {
        Shop existing = shop(12L, 30, LocalDateTime.now().plusDays(30));
        existing.setLoginStateProfile("jd-jingming-prefs-d");
        existing.setLoginStateSha256("old-sha");
        existing.setLoginStateSize(3L);
        existing.setLoginStateBlob("old".getBytes());
        existing.setLoginStateArtifactCreatedAt(LocalDateTime.of(2026, 6, 13, 10, 0));
        when(shopRepository.findByIdAndDeleted(12L, (byte) 0)).thenReturn(Optional.of(existing));

        Shop result = shopService.updateLoginState(
                12L,
                "jd-jingming-prefs-d",
                "{}",
                "new".getBytes(),
                "new-sha",
                LocalDateTime.of(2026, 6, 13, 9, 59)
        );

        assertThat(result.getLoginStateSha256()).isEqualTo("old-sha");
        verify(shopRepository, org.mockito.Mockito.never()).save(existing);
    }

    @Test
    void updateLoginStateStoresNewerArtifact() {
        Shop existing = shop(13L, 30, LocalDateTime.now().plusDays(30));
        existing.setLoginStateArtifactCreatedAt(LocalDateTime.of(2026, 6, 13, 10, 0));
        when(shopRepository.findByIdAndDeleted(13L, (byte) 0)).thenReturn(Optional.of(existing));
        when(shopRepository.save(existing)).thenReturn(existing);

        Shop result = shopService.updateLoginState(
                13L,
                "jd-jingming-prefs-d",
                "{}",
                "new".getBytes(),
                "new-sha",
                LocalDateTime.of(2026, 6, 13, 10, 1)
        );

        assertThat(result.getLoginStateSha256()).isEqualTo("new-sha");
        assertThat(result.getLoginStateArtifactCreatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 13, 10, 1));
        verify(shopRepository).save(existing);
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
