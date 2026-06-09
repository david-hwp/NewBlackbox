package com.duodian.admin.service;

import com.duodian.admin.entity.ComputeDeduction;
import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ComputeDeductionRepository;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                "CONSUME".equals(log.getType())
                        && log.getAmount() == 1
                        && "京东秒送".equals(log.getPlatform())
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

    @Test
    void getLatestReclaimableSubtractsReceiverConsumptionAndAlreadyReclaimedAmount() {
        User fromUser = user(1L, "13800000001", 2, 0);
        User receiver = user(2L, "13800000002", 7, 0);
        TransactionLog giftLog = giftLog(100L, 1L, "13800000002", 10);
        when(userRepository.findByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(receiver));
        stubLatestGift(giftLog);
        when(transactionLogRepository.sumConsumedAfter(2L, (byte) 0, giftLog.getCreatedAt(), 100L)).thenReturn(3);
        when(transactionLogRepository.sumReclaimedForSourceLog(1L, (byte) 0, 100L)).thenReturn(2);

        var response = computeService.getLatestReclaimable(1L, " 13800000002 ");

        assertThat(response.getGiftLogId()).isEqualTo(100L);
        assertThat(response.getGiftAmount()).isEqualTo(10);
        assertThat(response.getReceiverConsumedAmount()).isEqualTo(3);
        assertThat(response.getAlreadyReclaimedAmount()).isEqualTo(2);
        assertThat(response.getReclaimableAmount()).isEqualTo(5);
        assertThat(response.getToBalance()).isEqualTo(7);
    }

    @Test
    void getLatestReclaimableThrowsWhenCurrentUserHasNoGiftToReceiver() {
        User fromUser = user(1L, "13800000001", 2, 0);
        User receiver = user(2L, "13800000002", 7, 0);
        when(userRepository.findByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(receiver));
        when(transactionLogRepository
                .findFirstByUserIdAndTypeAndToPhoneAndRelatedLogIdIsNullAndRemarkStartingWithAndDeletedOrderByCreatedAtDescIdDesc(
                        1L,
                        "OUT",
                        "13800000002",
                        "赠送算力给 ",
                        (byte) 0
                )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> computeService.getLatestReclaimable(1L, "13800000002"))
                .hasMessage("未查询到您给对方的赠送记录");
    }

    @Test
    void reclaimComputeRevalidatesWithSharedCalculationAndWritesPairedLogs() {
        User fromUser = user(1L, "13800000001", 2, 0);
        User receiver = user(2L, "13800000002", 8, 7);
        TransactionLog giftLog = giftLog(100L, 1L, "13800000002", 10);
        when(userRepository.findWithLockByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findWithLockByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(receiver));
        stubLatestGift(giftLog);
        when(transactionLogRepository.sumConsumedAfter(2L, (byte) 0, giftLog.getCreatedAt(), 100L)).thenReturn(3);
        when(transactionLogRepository.sumReclaimedForSourceLog(1L, (byte) 0, 100L)).thenReturn(2, 6);

        var response = computeService.reclaimCompute(1L, "13800000002", 100L, 4);

        assertThat(fromUser.getComputeBalance()).isEqualTo(6);
        assertThat(receiver.getComputeBalance()).isEqualTo(4);
        assertThat(receiver.getNonTransferableComputeBalance()).isEqualTo(4);
        assertThat(response.getReclaimedAmount()).isEqualTo(4);
        assertThat(response.getFromBalance()).isEqualTo(6);
        assertThat(response.getToBalance()).isEqualTo(4);
        assertThat(response.getReclaimableAmount()).isEqualTo(1);
        verify(userRepository).save(fromUser);
        verify(userRepository).save(receiver);

        ArgumentCaptor<TransactionLog> logCaptor = ArgumentCaptor.forClass(TransactionLog.class);
        verify(transactionLogRepository, times(2)).save(logCaptor.capture());
        List<TransactionLog> logs = logCaptor.getAllValues();
        assertThat(logs).anySatisfy(log -> {
            assertThat(log.getUserId()).isEqualTo(1L);
            assertThat(log.getType()).isEqualTo("IN");
            assertThat(log.getAmount()).isEqualTo(4);
            assertThat(log.getFromPhone()).isEqualTo("13800000002");
            assertThat(log.getRelatedLogId()).isEqualTo(100L);
            assertThat(log.getRemark()).isEqualTo("从13800000002取回");
        });
        assertThat(logs).anySatisfy(log -> {
            assertThat(log.getUserId()).isEqualTo(2L);
            assertThat(log.getType()).isEqualTo("OUT");
            assertThat(log.getAmount()).isEqualTo(4);
            assertThat(log.getToPhone()).isEqualTo("13800000001");
            assertThat(log.getRelatedLogId()).isEqualTo(100L);
            assertThat(log.getRemark()).isEqualTo("赠送方13800000001取回");
        });
    }

    @Test
    void reclaimComputeRejectsWhenLatestGiftChangedAfterQuery() {
        User fromUser = user(1L, "13800000001", 2, 0);
        User receiver = user(2L, "13800000002", 8, 0);
        TransactionLog giftLog = giftLog(101L, 1L, "13800000002", 10);
        when(userRepository.findWithLockByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findWithLockByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(receiver));
        stubLatestGift(giftLog);
        when(transactionLogRepository.sumConsumedAfter(2L, (byte) 0, giftLog.getCreatedAt(), 101L)).thenReturn(0);
        when(transactionLogRepository.sumReclaimedForSourceLog(1L, (byte) 0, 101L)).thenReturn(0);

        assertThatThrownBy(() -> computeService.reclaimCompute(1L, "13800000002", 100L, 1))
                .hasMessage("对方新增了消耗，请重新查询可取回算力");

        verify(userRepository, never()).save(argThat(user -> true));
        verify(transactionLogRepository, never()).save(argThat(log -> true));
    }

    @Test
    void reclaimComputeRejectsWhenReceiverConsumedAfterQuery() {
        User fromUser = user(1L, "13800000001", 2, 0);
        User receiver = user(2L, "13800000002", 8, 0);
        TransactionLog giftLog = giftLog(100L, 1L, "13800000002", 10);
        when(userRepository.findWithLockByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findWithLockByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(receiver));
        stubLatestGift(giftLog);
        when(transactionLogRepository.sumConsumedAfter(2L, (byte) 0, giftLog.getCreatedAt(), 100L)).thenReturn(8);
        when(transactionLogRepository.sumReclaimedForSourceLog(1L, (byte) 0, 100L)).thenReturn(1);

        assertThatThrownBy(() -> computeService.reclaimCompute(1L, "13800000002", 100L, 2))
                .hasMessage("对方新增了消耗，请重新查询可取回算力");

        verify(userRepository, never()).save(argThat(user -> true));
        verify(transactionLogRepository, never()).save(argThat(log -> true));
    }

    @Test
    void reclaimComputeRejectsWhenReceiverCurrentBalanceIsInsufficient() {
        User fromUser = user(1L, "13800000001", 2, 0);
        User receiver = user(2L, "13800000002", 1, 0);
        TransactionLog giftLog = giftLog(100L, 1L, "13800000002", 10);
        when(userRepository.findWithLockByIdAndDeleted(1L, (byte) 0)).thenReturn(Optional.of(fromUser));
        when(userRepository.findWithLockByPhoneAndDeleted("13800000002", (byte) 0)).thenReturn(Optional.of(receiver));
        stubLatestGift(giftLog);
        when(transactionLogRepository.sumConsumedAfter(2L, (byte) 0, giftLog.getCreatedAt(), 100L)).thenReturn(0);
        when(transactionLogRepository.sumReclaimedForSourceLog(1L, (byte) 0, 100L)).thenReturn(0);

        assertThatThrownBy(() -> computeService.reclaimCompute(1L, "13800000002", 100L, 2))
                .hasMessage("对方新增了消耗，请重新查询可取回算力");

        verify(userRepository, never()).save(argThat(user -> true));
        verify(transactionLogRepository, never()).save(argThat(log -> true));
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

    private TransactionLog giftLog(Long id, Long fromUserId, String toPhone, int amount) {
        TransactionLog log = new TransactionLog();
        log.setId(id);
        log.setUserId(fromUserId);
        log.setType("OUT");
        log.setAmount(amount);
        log.setToPhone(toPhone);
        log.setToName("receiver");
        log.setRemark("赠送算力给 " + toPhone);
        log.setCreatedAt(LocalDateTime.of(2026, 6, 10, 1, 0).plusMinutes(id));
        return log;
    }

    private void stubLatestGift(TransactionLog giftLog) {
        when(transactionLogRepository
                .findFirstByUserIdAndTypeAndToPhoneAndRelatedLogIdIsNullAndRemarkStartingWithAndDeletedOrderByCreatedAtDescIdDesc(
                        giftLog.getUserId(),
                        "OUT",
                        giftLog.getToPhone(),
                        "赠送算力给 ",
                        (byte) 0
                )).thenReturn(Optional.of(giftLog));
    }
}
