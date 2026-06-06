package com.duodian.admin.service;

import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.TransactionLogRepository;
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
    private final TransactionLogRepository transactionLogRepository = mock(TransactionLogRepository.class);
    private final UserService userService = new UserService(
            userRepository,
            shopRepository,
            passwordService,
            transactionLogRepository
    );

    @Test
    void refreshShopStatsUsesRealShopAndDistinctPlatformCounts() {
        User user = new User();
        user.setId(7L);
        user.setUsername("user-7");
        user.setPhone("13800000007");
        user.setShopCount(0);
        user.setPlatformCount(0);
        when(userRepository.findByIdAndDeleted(7L, (byte) 0)).thenReturn(Optional.of(user));
        when(shopRepository.countRealShopsByUserId(7L, (byte) 0)).thenReturn(3L);
        when(shopRepository.countRealPlatformsByUserId(7L, (byte) 0)).thenReturn(2L);
        when(userRepository.save(user)).thenReturn(user);

        User refreshed = userService.refreshShopStats(7L);

        assertThat(refreshed.getShopCount()).isEqualTo(3);
        assertThat(refreshed.getPlatformCount()).isEqualTo(2);
        verify(userRepository).save(user);
    }

    @Test
    void updateClampsNonTransferableBalanceToTotalBalance() {
        User existing = new User();
        existing.setId(8L);
        existing.setUsername("old");
        existing.setPhone("13800000008");
        existing.setComputeBalance(10);
        existing.setNonTransferableComputeBalance(0);
        User request = new User();
        request.setUsername("new");
        request.setComputeBalance(3);
        request.setNonTransferableComputeBalance(9);
        request.setShopCount(0);
        request.setPlatformCount(0);
        when(userRepository.findByIdAndDeleted(8L, (byte) 0)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        User updated = userService.update(8L, request);

        assertThat(updated.getComputeBalance()).isEqualTo(3);
        assertThat(updated.getNonTransferableComputeBalance()).isEqualTo(3);
    }
}
