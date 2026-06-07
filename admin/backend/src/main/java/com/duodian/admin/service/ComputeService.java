package com.duodian.admin.service;

import com.duodian.admin.entity.ComputeDeduction;
import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ComputeDeductionRepository;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ComputeService {
    private static final byte ACTIVE = 0;

    private final UserRepository userRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ComputeDeductionRepository computeDeductionRepository;

    public ComputeService(
            UserRepository userRepository,
            TransactionLogRepository transactionLogRepository,
            ComputeDeductionRepository computeDeductionRepository
    ) {
        this.userRepository = userRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.computeDeductionRepository = computeDeductionRepository;
    }

    public Integer getBalance(Long userId) {
        return userRepository.findByIdAndDeleted(userId, ACTIVE)
                .map(User::getComputeBalance)
                .orElse(0);
    }

    public Optional<User> lockActiveUser(Long userId) {
        return userRepository.findWithLockByIdAndDeleted(userId, ACTIVE);
    }

    @Transactional
    public boolean deductCompute(Long userId, String shopId, String shopName, String platform) {
        return deductCompute(userId, shopId, shopName, platform, "店铺上报扣减: ");
    }

    @Transactional
    public boolean deductComputeForRenewal(Long userId, String shopId, String shopName, String platform) {
        return deductCompute(userId, shopId, shopName, platform, "店铺续期扣减: ");
    }

    @Transactional
    public boolean deductComputeForClone(Long userId, String cloneInstanceId, String shopName, String platform) {
        return deductCompute(userId, cloneInstanceId, shopName, platform, "店铺创建扣减: ");
    }

    @Transactional
    public DeductionResult deductComputeForCloneCreate(
            Long userId,
            String cloneInstanceId,
            String operationKey,
            String platform,
            String displayName
    ) {
        return deductComputeForCloneOperation(
                userId,
                cloneInstanceId,
                "CREATE",
                operationKey,
                platform,
                displayName,
                "店铺创建扣减: "
        );
    }

    public Optional<ComputeDeduction> findExistingCloneCreateOperation(Long userId, String operationKey) {
        String normalizedOperationKey = normalizeRequired(operationKey, "操作标识不能为空");
        return computeDeductionRepository.findFirstByUserIdAndDeductionTypeAndOperationKeyAndDeleted(
                userId,
                "CREATE",
                normalizedOperationKey,
                ACTIVE
        );
    }

    @Transactional
    public DeductionResult deductComputeForCloneRenew(
            Long userId,
            String cloneInstanceId,
            String operationKey,
            String platform,
            String displayName
    ) {
        return deductComputeForCloneOperation(
                userId,
                cloneInstanceId,
                "RENEW",
                operationKey,
                platform,
                displayName,
                "店铺续期扣减: "
        );
    }

    private DeductionResult deductComputeForCloneOperation(
            Long userId,
            String cloneInstanceId,
            String deductionType,
            String operationKey,
            String platform,
            String displayName,
            String remarkPrefix
    ) {
        String normalizedCloneId = normalizeRequired(cloneInstanceId, "店铺标识不能为空");
        String normalizedOperationKey = normalizeRequired(operationKey, "操作标识不能为空");
        Optional<ComputeDeduction> existing = computeDeductionRepository
                .findByUserIdAndCloneInstanceIdAndDeductionTypeAndOperationKeyAndDeleted(
                        userId,
                        normalizedCloneId,
                        deductionType,
                        normalizedOperationKey,
                        ACTIVE
                );
        if (existing.isPresent()) {
            return new DeductionResult(false, true, existing.get().getTransactionLogId());
        }

        User user = userRepository.findByIdAndDeleted(userId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        int balance = user.getComputeBalance() == null ? 0 : user.getComputeBalance();
        if (balance < 1) {
            return DeductionResult.insufficient();
        }

        user.setComputeBalance(balance - 1);
        int nonTransferable = user.getNonTransferableComputeBalance() == null
                ? 0
                : user.getNonTransferableComputeBalance();
        if (nonTransferable > 0) {
            user.setNonTransferableComputeBalance(Math.min(nonTransferable - 1, user.getComputeBalance()));
        } else {
            user.setNonTransferableComputeBalance(0);
        }
        userRepository.save(user);

        TransactionLog log = new TransactionLog();
        log.setUserId(userId);
        log.setType("CONSUME");
        log.setAmount(1);
        log.setPlatform(displayPlatformName(platform));
        log.setShopName(displayName);
        log.setRemark(remarkPrefix + normalizedCloneId);
        TransactionLog savedLog = transactionLogRepository.save(log);

        ComputeDeduction deduction = new ComputeDeduction();
        deduction.setUserId(userId);
        deduction.setCloneInstanceId(normalizedCloneId);
        deduction.setDeductionType(deductionType);
        deduction.setOperationKey(normalizedOperationKey);
        deduction.setAmount(1);
        deduction.setTransactionLogId(savedLog.getId());
        computeDeductionRepository.save(deduction);

        return new DeductionResult(true, true, savedLog.getId());
    }

    private boolean deductCompute(Long userId, String shopId, String shopName, String platform, String remarkPrefix) {
        User user = userRepository.findByIdAndDeleted(userId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        int balance = user.getComputeBalance() == null ? 0 : user.getComputeBalance();

        if (balance < 1) {
            return false;
        }

        user.setComputeBalance(balance - 1);
        int nonTransferable = user.getNonTransferableComputeBalance() == null
                ? 0
                : user.getNonTransferableComputeBalance();
        if (nonTransferable > 0) {
            user.setNonTransferableComputeBalance(Math.min(nonTransferable - 1, user.getComputeBalance()));
        } else {
            user.setNonTransferableComputeBalance(0);
        }
        userRepository.save(user);

        TransactionLog log = new TransactionLog();
        log.setUserId(userId);
        log.setType("CONSUME");
        log.setAmount(1);
        log.setPlatform(displayPlatformName(platform));
        log.setShopName(shopName);
        log.setRemark(remarkPrefix + shopId);
        transactionLogRepository.save(log);

        return true;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String displayPlatformName(String platform) {
        if (platform == null || platform.isBlank()) {
            return platform;
        }
        return switch (platform.trim().toLowerCase()) {
            case "jd" -> "京东秒送";
            case "meituan" -> "美团";
            case "taobao" -> "淘宝";
            case "kuaishou" -> "快手";
            case "xiaohongshu" -> "小红书";
            case "ali" -> "阿里本地";
            default -> platform.trim();
        };
    }

    @Transactional
    public void giftCompute(Long fromUserId, String toPhone, Integer amount) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("赠送数量必须大于0");
        }

        User fromUser = userRepository.findByIdAndDeleted(fromUserId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("转出用户不存在"));

        int balance = fromUser.getComputeBalance() == null ? 0 : fromUser.getComputeBalance();
        int nonTransferable = fromUser.getNonTransferableComputeBalance() == null
                ? 0
                : fromUser.getNonTransferableComputeBalance();
        int transferableBalance = Math.max(0, balance - nonTransferable);

        if (transferableBalance < amount) {
            throw new RuntimeException("可转赠算力余额不足");
        }

        User toUser = userRepository.findByPhoneAndDeleted(toPhone, ACTIVE)
                .orElseThrow(() -> new RuntimeException("接收用户不存在"));

        if (fromUserId.equals(toUser.getId())) {
            throw new RuntimeException("不能赠送给自己");
        }

        fromUser.setComputeBalance(balance - amount);
        int toBalance = toUser.getComputeBalance() == null ? 0 : toUser.getComputeBalance();
        if (toUser.getNonTransferableComputeBalance() == null) {
            toUser.setNonTransferableComputeBalance(0);
        }
        fromUser.setNonTransferableComputeBalance(Math.min(nonTransferable, fromUser.getComputeBalance()));
        toUser.setComputeBalance(toBalance + amount);

        userRepository.save(fromUser);
        userRepository.save(toUser);

        TransactionLog outLog = new TransactionLog();
        outLog.setUserId(fromUserId);
        outLog.setType("OUT");
        outLog.setAmount(amount);
        outLog.setToPhone(toPhone);
        outLog.setToName(toUser.getUsername());
        outLog.setRemark("赠送算力给 " + toPhone);
        transactionLogRepository.save(outLog);

        TransactionLog inLog = new TransactionLog();
        inLog.setUserId(toUser.getId());
        inLog.setType("IN");
        inLog.setAmount(amount);
        inLog.setFromPhone(fromUser.getPhone());
        inLog.setFromName(fromUser.getUsername());
        inLog.setRemark("收到 " + fromUser.getPhone() + " 赠送的算力");
        transactionLogRepository.save(inLog);
    }

    public static class DeductionResult {
        private final boolean deducted;
        private final boolean success;
        private final Long transactionLogId;

        public DeductionResult(boolean deducted, boolean success, Long transactionLogId) {
            this.deducted = deducted;
            this.success = success;
            this.transactionLogId = transactionLogId;
        }

        public static DeductionResult insufficient() {
            return new DeductionResult(false, false, null);
        }

        public boolean isDeducted() { return deducted; }
        public boolean isSuccess() { return success; }
        public Long getTransactionLogId() { return transactionLogId; }
    }
}
