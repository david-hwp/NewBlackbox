package com.duodian.admin.service;

import com.duodian.admin.entity.User;
import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.repository.PlatformConfigRepository;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.repository.UserRepository;
import com.duodian.admin.repository.ChannelRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final PlatformConfigRepository platformConfigRepository = mock(PlatformConfigRepository.class);
    private final PasswordService passwordService = mock(PasswordService.class);
    private final TransactionLogRepository transactionLogRepository = mock(TransactionLogRepository.class);
    private final ChannelRepository channelRepository = mock(ChannelRepository.class);
    private final UserService userService = new UserService(
            userRepository,
            shopRepository,
            platformConfigRepository,
            passwordService,
            transactionLogRepository,
            channelRepository
    );

    @Test
    void refreshShopStatsUsesCloneCountAndAvailablePlatformCount() {
        User user = new User();
        user.setId(7L);
        user.setUsername("user-7");
        user.setPhone("13800000007");
        user.setShopCount(0);
        user.setPlatformCount(0);
        when(userRepository.findByIdAndDeleted(7L, (byte) 0)).thenReturn(Optional.of(user));
        when(shopRepository.countByUserIdAndDeleted(7L, (byte) 0)).thenReturn(3L);
        when(platformConfigRepository.countByDeletedAndAvailable((byte) 0, true)).thenReturn(4L);
        when(userRepository.save(user)).thenReturn(user);

        User refreshed = userService.refreshShopStats(7L);

        assertThat(refreshed.getShopCount()).isEqualTo(3);
        assertThat(refreshed.getPlatformCount()).isEqualTo(4);
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
        verify(transactionLogRepository).save(argThat(log ->
                log.getUserId().equals(8L)
                        && "CONSUME".equals(log.getType())
                        && log.getAmount().equals(7)
                        && "管理员扣除".equals(log.getRemark())
        ));
    }

    @Test
    void updateCreatesAdminIncreaseLogWhenComputeBalanceIncreases() {
        User existing = new User();
        existing.setId(9L);
        existing.setUsername("old");
        existing.setPhone("13800000009");
        existing.setComputeBalance(2);
        existing.setNonTransferableComputeBalance(0);
        User request = new User();
        request.setUsername("new");
        request.setComputeBalance(5);
        request.setNonTransferableComputeBalance(0);
        request.setShopCount(0);
        request.setPlatformCount(0);
        when(userRepository.findByIdAndDeleted(9L, (byte) 0)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.update(9L, request);

        verify(transactionLogRepository).save(argThat(log ->
                log.getUserId().equals(9L)
                        && "IN".equals(log.getType())
                        && log.getAmount().equals(3)
                        && "管理员增加".equals(log.getRemark())
        ));
    }

    @Test
    void updateCreatesAdminPhoneMinutesIncreaseLogWhenBalanceIncreases() {
        User existing = new User();
        existing.setId(12L);
        existing.setUsername("old");
        existing.setPhone("13800000012");
        existing.setComputeBalance(2);
        existing.setNonTransferableComputeBalance(0);
        existing.setPhoneMinutesBalance(5);
        User request = new User();
        request.setUsername("new");
        request.setComputeBalance(2);
        request.setNonTransferableComputeBalance(0);
        request.setPhoneMinutesBalance(15);
        when(userRepository.findByIdAndDeleted(12L, (byte) 0)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.update(12L, request);

        verify(transactionLogRepository).save(argThat(log ->
                log.getUserId().equals(12L)
                        && "PHONE_IN".equals(log.getType())
                        && log.getAmount().equals(10)
                        && "管理员增加话费".equals(log.getRemark())
        ));
    }

    @Test
    void updateDoesNotCreateLogWhenComputeBalanceUnchanged() {
        User existing = new User();
        existing.setId(10L);
        existing.setUsername("old");
        existing.setPhone("13800000010");
        existing.setComputeBalance(5);
        existing.setNonTransferableComputeBalance(0);
        User request = new User();
        request.setUsername("new");
        request.setComputeBalance(5);
        request.setNonTransferableComputeBalance(0);
        request.setShopCount(0);
        request.setPlatformCount(0);
        when(userRepository.findByIdAndDeleted(10L, (byte) 0)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.update(10L, request);

        verify(transactionLogRepository, never()).save(argThat((TransactionLog log) ->
                "管理员增加".equals(log.getRemark()) || "管理员扣除".equals(log.getRemark())
        ));
    }

    @Test
    void updateSubscriptionSetsPlanExpiryAndWritesZeroAmountLog() {
        User existing = new User();
        existing.setId(11L);
        existing.setUsername("user-11");
        existing.setPhone("13800000011");
        existing.setComputeBalance(1);
        existing.setNonTransferableComputeBalance(0);
        when(userRepository.findByIdAndDeleted(11L, (byte) 0)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        User updated = userService.updateSubscription(11L, "quarterly");

        assertThat(updated.getSubscriptionPlan()).isEqualTo(UserService.PLAN_QUARTERLY);
        assertThat(updated.getSubscriptionExpiresAt()).isAfter(java.time.LocalDateTime.now().plusMonths(2));
        assertThat(updated.getSubscriptionUpdatedAt()).isNotNull();
        verify(transactionLogRepository).save(argThat(log ->
                log.getUserId().equals(11L)
                        && "IN".equals(log.getType())
                        && log.getAmount().equals(0)
                        && "管理员开通订阅: 季度".equals(log.getRemark())
        ));
    }

    @Test
    void createChannelAdminRejectsPhoneAlreadyUsedByNormalUser() {
        User request = new User();
        request.setUsername("channel admin");
        request.setPhone("13800000011");
        request.setPassword("password");
        request.setRole("CHANNEL");
        request.setChannelId(2L);
        User existingNormalUser = new User();
        existingNormalUser.setId(11L);
        existingNormalUser.setPhone("13800000011");
        existingNormalUser.setRole("USER");
        when(userRepository.findAllByPhoneAndDeleted("13800000011", (byte) 0)).thenReturn(java.util.List.of(existingNormalUser));

        assertThatThrownBy(() -> userService.create(request))
                .hasMessage("管理员手机号已存在");

        verify(userRepository, never()).save(argThat(user -> true));
    }
}
