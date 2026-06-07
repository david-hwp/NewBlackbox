package com.duodian.admin.service;

import com.duodian.admin.entity.ComputeDeduction;
import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ComputeDeductionRepository;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComputeServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionLogRepository transactionLogRepository = mock(TransactionLogRepository.class);
    private final ComputeDeductionRepository computeDeductionRepository = mock(ComputeDeductionRepository.class);
    private final ComputeService computeService = new ComputeService(
            userRepository,
            transactionLogRepository,
            computeDeductionRepository
    );

    @Test
    void giftComputeOnlyAllowsTransferableBalance() {
        User fromUser = user(1L, "13800000001", 2, 2);
        User toUser = user(2L, "13800000002", 0, 0);
        when(userRepository.findByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(toUser));

        assertThatThrownBy(() -> computeService.giftCompute(1L, "13800000002", 1))
                .hasMessage("可转赠算力余额不足");
    }

    @Test
    void deductComputeConsumesNonTransferableBalanceFirst() {
        User user = user(1L, "13800000001", 2, 2);
        when(userRepository.findByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(user));

        boolean deducted = computeService.deductCompute(1L, "shop-1", "测试店铺", "jd");

        assertThat(deducted).isTrue();
        assertThat(user.getComputeBalance()).isEqualTo(1);
        assertThat(user.getNonTransferableComputeBalance()).isEqualTo(1);
        verify(userRepository).save(user);
        verify(transactionLogRepository).save(argThat(log ->
                "CONSUME".equals(log.getType()) && log.getAmount() == 1
        ));
    }

    @Test
    void cloneCreateDeductionIsIdempotentAndUsesCloneIdRemark() {
        User user = user(1L, "13800000001", 2, 2);
        when(computeDeductionRepository.findByUserIdAndCloneInstanceIdAndDeductionTypeAndOperationKeyAndDeleted(
                1L,
                "CLN1-abc",
                "CREATE",
                "op-1",
                (byte) 0
        )).thenReturn(Optional.empty());
        when(userRepository.findByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(user));
        when(transactionLogRepository.save(argThat(log ->
                "CONSUME".equals(log.getType())
                        && "店铺创建扣减: CLN1-abc".equals(log.getRemark())
        ))).thenAnswer(invocation -> {
            TransactionLog log = invocation.getArgument(0);
            log.setId(77L);
            return log;
        });

        ComputeService.DeductionResult result = computeService.deductComputeForCloneCreate(
                1L,
                "CLN1-abc",
                "op-1",
                "jd",
                "新增店铺-[1]"
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isDeducted()).isTrue();
        assertThat(user.getComputeBalance()).isEqualTo(1);
        verify(computeDeductionRepository).save(argThat(deduction ->
                "CLN1-abc".equals(deduction.getCloneInstanceId())
                        && "CREATE".equals(deduction.getDeductionType())
                        && "op-1".equals(deduction.getOperationKey())
                        && deduction.getTransactionLogId().equals(77L)
        ));
    }

    @Test
    void duplicateCloneDeductionOperationDoesNotChargeAgain() {
        ComputeDeduction existing = new ComputeDeduction();
        existing.setTransactionLogId(77L);
        when(computeDeductionRepository.findByUserIdAndCloneInstanceIdAndDeductionTypeAndOperationKeyAndDeleted(
                1L,
                "CLN1-abc",
                "RENEW",
                "op-1",
                (byte) 0
        )).thenReturn(Optional.of(existing));

        ComputeService.DeductionResult result = computeService.deductComputeForCloneRenew(
                1L,
                "CLN1-abc",
                "op-1",
                "jd",
                "店铺"
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isDeducted()).isFalse();
        assertThat(result.getTransactionLogId()).isEqualTo(77L);
        verify(userRepository, never()).save(argThat(user -> true));
        verify(transactionLogRepository, never()).save(argThat(log -> true));
    }

    @Test
    void giftComputeTransfersOnlyTransferablePortion() {
        User fromUser = user(1L, "13800000001", 5, 2);
        User toUser = user(2L, "13800000002", 0, 0);
        when(userRepository.findByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(toUser));

        computeService.giftCompute(1L, "13800000002", 3);

        assertThat(fromUser.getComputeBalance()).isEqualTo(2);
        assertThat(fromUser.getNonTransferableComputeBalance()).isEqualTo(2);
        assertThat(toUser.getComputeBalance()).isEqualTo(3);
        assertThat(toUser.getNonTransferableComputeBalance()).isZero();
        verify(transactionLogRepository).save(argThat(log -> "OUT".equals(log.getType())));
        verify(transactionLogRepository).save(argThat(log -> "IN".equals(log.getType())));
    }

    private User user(Long id, String phone, int balance, int nonTransferableBalance) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setPhone(phone);
        user.setComputeBalance(balance);
        user.setNonTransferableComputeBalance(nonTransferableBalance);
        return user;
    }
}
