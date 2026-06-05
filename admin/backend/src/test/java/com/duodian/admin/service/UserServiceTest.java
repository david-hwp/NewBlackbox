package com.duodian.admin.service;

import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final PasswordService passwordService = mock(PasswordService.class);
    private final UserService userService = new UserService(userRepository, shopRepository, passwordService);

    @Test
    void refreshShopStatsUsesRealShopAndDistinctPlatformCounts() {
        User user = new User();
        user.setId(7L);
        user.setUsername("user-7");
        user.setPhone("13800000007");
        user.setShopCount(0);
        user.setPlatformCount(0);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(shopRepository.countRealShopsByUserId(7L)).thenReturn(3L);
        when(shopRepository.countRealPlatformsByUserId(7L)).thenReturn(2L);
        when(userRepository.save(user)).thenReturn(user);

        User refreshed = userService.refreshShopStats(7L);

        assertThat(refreshed.getShopCount()).isEqualTo(3);
        assertThat(refreshed.getPlatformCount()).isEqualTo(2);
        verify(userRepository).save(user);
    }
}
