package com.duodian.admin.service;

import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ComputeService {

    private final UserRepository userRepository;
    private final TransactionLogRepository transactionLogRepository;

    public ComputeService(UserRepository userRepository, TransactionLogRepository transactionLogRepository) {
        this.userRepository = userRepository;
        this.transactionLogRepository = transactionLogRepository;
    }

    public Integer getBalance(Long userId) {
        return userRepository.findById(userId)
                .map(User::getComputeBalance)
                .orElse(0);
    }

    @Transactional
    public boolean deductCompute(Long userId, String shopId, String shopName, String platform) {
        return deductCompute(userId, shopId, shopName, platform, "店铺上报扣减: ");
    }

    @Transactional
    public boolean deductComputeForRenewal(Long userId, String shopId, String shopName, String platform) {
        return deductCompute(userId, shopId, shopName, platform, "店铺续期扣减: ");
    }

    private boolean deductCompute(Long userId, String shopId, String shopName, String platform, String remarkPrefix) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (user.getComputeBalance() < 1) {
            return false;
        }

        user.setComputeBalance(user.getComputeBalance() - 1);
        userRepository.save(user);

        TransactionLog log = new TransactionLog();
        log.setUserId(userId);
        log.setType("CONSUME");
        log.setAmount(1);
        log.setPlatform(platform);
        log.setShopName(shopName);
        log.setRemark(remarkPrefix + shopId);
        transactionLogRepository.save(log);

        return true;
    }

    @Transactional
    public void giftCompute(Long fromUserId, String toPhone, Integer amount) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("赠送数量必须大于0");
        }

        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new RuntimeException("转出用户不存在"));

        if (fromUser.getComputeBalance() < amount) {
            throw new RuntimeException("算力余额不足");
        }

        User toUser = userRepository.findByPhone(toPhone)
                .orElseThrow(() -> new RuntimeException("接收用户不存在"));

        if (fromUserId.equals(toUser.getId())) {
            throw new RuntimeException("不能赠送给自己");
        }

        fromUser.setComputeBalance(fromUser.getComputeBalance() - amount);
        toUser.setComputeBalance(toUser.getComputeBalance() + amount);

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
}
