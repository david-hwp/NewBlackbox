package com.duodian.admin.service;

import com.duodian.admin.entity.Shop;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopServiceTest {
    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ShopService shopService = new ShopService(shopRepository, userRepository);

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
    void updateKeepsExistingCardSortOrderWhenRequestOmitsIt() {
        Shop existing = shop(14L, 30, LocalDateTime.now().plusDays(30));
        existing.setCardSortOrder(50);
        Shop request = shop(14L, 30, LocalDateTime.now().plusDays(30));
        request.setCardSortOrder(null);
        when(shopRepository.findByIdAndDeleted(14L, (byte) 0)).thenReturn(Optional.of(existing));
        when(shopRepository.save(existing)).thenReturn(existing);

        Shop updated = shopService.update(14L, request);

        assertThat(updated.getCardSortOrder()).isEqualTo(50);
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

    @Test
    void reorderUserPlatformShopsUsesSystemShopIdsAndKeepsScopeTogether() {
        Shop first = shop(21L, 30, LocalDateTime.now().plusDays(30));
        first.setPackageName("com.jd.mrd.jingming");
        first.setCardSortOrder(10);
        Shop second = shop(22L, 30, LocalDateTime.now().plusDays(30));
        second.setPackageName("com.jd.mrd.jingming");
        second.setCardSortOrder(20);
        Shop third = shop(23L, 30, LocalDateTime.now().plusDays(30));
        third.setPackageName("com.jd.mrd.jingming");
        third.setCardSortOrder(30);
        when(shopRepository.findByIdInAndDeleted(List.of(23L, 21L), (byte) 0)).thenReturn(List.of(third, first));
        when(shopRepository.findByUserIdAndDeletedOrderByCardSortOrderAscCreatedAtDescIdDesc(1L, (byte) 0))
                .thenReturn(List.of(first, second, third), List.of(third, first, second));

        List<Shop> result = shopService.reorderUserPlatformShops(1L, List.of(23L, 21L));

        assertThat(result).extracting(Shop::getId).containsExactly(23L, 21L, 22L);
        verify(shopRepository).saveAll(argThat(saved -> {
            List<Shop> shops = new ArrayList<>();
            saved.forEach(shops::add);
            return shops.size() == 3
                    && shops.get(0).getId().equals(23L)
                    && shops.get(0).getCardSortOrder().equals(10)
                    && shops.get(1).getId().equals(21L)
                    && shops.get(1).getCardSortOrder().equals(20)
                    && shops.get(2).getId().equals(22L)
                    && shops.get(2).getCardSortOrder().equals(30);
        }));
    }

    @Test
    void reorderUserPlatformShopsRejectsCrossPlatformIds() {
        Shop jd = shop(31L, 30, LocalDateTime.now().plusDays(30));
        jd.setPackageName("com.jd.mrd.jingming");
        jd.setPlatform("jd");
        Shop ele = shop(32L, 30, LocalDateTime.now().plusDays(30));
        ele.setPackageName("me.ele.napos");
        ele.setPlatform("ele");
        when(shopRepository.findByIdInAndDeleted(List.of(31L, 32L), (byte) 0)).thenReturn(List.of(jd, ele));

        assertThatThrownBy(() -> shopService.reorderUserPlatformShops(1L, List.of(31L, 32L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("一次只能调整同一平台下的店铺排序");

        verify(shopRepository, org.mockito.Mockito.never()).saveAll(org.mockito.Mockito.any());
    }

    @Test
    void createAssignsNextCardSortOrderWithinUserPlatform() {
        Shop existing = shop(41L, 30, LocalDateTime.now().plusDays(30));
        existing.setPackageName("com.jd.mrd.jingming");
        existing.setCardSortOrder(30);
        Shop otherPlatform = shop(42L, 30, LocalDateTime.now().plusDays(30));
        otherPlatform.setPackageName("me.ele.napos");
        otherPlatform.setCardSortOrder(90);
        Shop request = shop(43L, 30, LocalDateTime.now().plusDays(30));
        request.setPackageName("com.jd.mrd.jingming");
        request.setCardSortOrder(null);
        when(shopRepository.findByUserIdAndDeletedOrderByCardSortOrderAscCreatedAtDescIdDesc(1L, (byte) 0))
                .thenReturn(List.of(existing, otherPlatform));
        when(shopRepository.save(request)).thenReturn(request);

        Shop created = shopService.create(request);

        assertThat(created.getCardSortOrder()).isEqualTo(40);
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
