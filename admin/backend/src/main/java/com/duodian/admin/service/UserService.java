package com.duodian.admin.service;

import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.entity.User;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.PlatformConfigRepository;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PlatformConfigRepository platformConfigRepository;
    private final PasswordService passwordService;
    private final TransactionLogRepository transactionLogRepository;
    private final ChannelRepository channelRepository;

    public UserService(
            UserRepository userRepository,
            ShopRepository shopRepository,
            PlatformConfigRepository platformConfigRepository,
            PasswordService passwordService,
            TransactionLogRepository transactionLogRepository,
            ChannelRepository channelRepository
    ) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.platformConfigRepository = platformConfigRepository;
        this.passwordService = passwordService;
        this.transactionLogRepository = transactionLogRepository;
        this.channelRepository = channelRepository;
    }

    public List<User> findAll() {
        return userRepository.findByDeleted(ACTIVE).stream()
                .map(this::withCurrentStats)
                .toList();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findByIdAndDeleted(id, ACTIVE);
    }

    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhoneAndDeleted(phone, ACTIVE);
    }

    public Optional<User> findByPhoneInChannel(String phone, Long channelId) {
        return userRepository.findFirstByPhoneAndChannelIdAndDeleted(phone, channelId, ACTIVE);
    }

    @Transactional
    public User refreshShopStats(Long userId) {
        User user = userRepository.findByIdAndDeleted(userId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        withCurrentStats(user);
        return userRepository.save(user);
    }

    public User withCurrentStats(User user) {
        if (user == null || user.getId() == null) {
            return user;
        }
        user.setShopCount(Math.toIntExact(shopRepository.countByUserIdAndDeleted(user.getId(), ACTIVE)));
        user.setPlatformCount(Math.toIntExact(platformConfigRepository.countByDeletedAndAvailable(ACTIVE, true)));
        return user;
    }

    public User create(User user) {
        normalizeUserChannel(user);
        validatePhoneUniqueness(user);
        user.setDeleted(ACTIVE);
        user.setApkChannel(normalizeApkChannel(user.getApkChannel()));
        user.setRole(normalizeRole(user.getRole()));
        user.setComputeBalance(user.getComputeBalance() == null ? 0 : user.getComputeBalance());
        user.setNonTransferableComputeBalance(normalizeNonTransferableBalance(user));
        user.setPassword(passwordService.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional
    public User update(Long id, User user) {
        User existing = userRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        int oldComputeBalance = existing.getComputeBalance() == null ? 0 : existing.getComputeBalance();
        int newComputeBalance = user.getComputeBalance() == null ? 0 : user.getComputeBalance();
        existing.setUsername(user.getUsername());
        existing.setAvatarUrl(user.getAvatarUrl());
        if (user.getChannelId() != null) {
            existing.setChannelId(user.getChannelId());
        }
        existing.setApkChannel(normalizeApkChannel(existing.getApkChannel()));
        existing.setComputeBalance(newComputeBalance);
        existing.setNonTransferableComputeBalance(normalizeNonTransferableBalance(user));
        withCurrentStats(existing);
        User saved = userRepository.save(existing);
        createAdminComputeAdjustmentLog(saved, newComputeBalance - oldComputeBalance);
        return saved;
    }

    @Transactional
    public User updateProfile(Long id, User user) {
        User existing = userRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        existing.setUsername(user.getUsername());
        existing.setAvatarUrl(user.getAvatarUrl());
        withCurrentStats(existing);
        return userRepository.save(existing);
    }

    public void delete(Long id) {
        User user = userRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setDeleted(DELETED);
        userRepository.save(user);
    }

    public User login(String phone, String password) {
        return login(phone, password, null);
    }

    public User login(String phone, String password, Long channelId) {
        User user = resolveLoginUser(phone, channelId);
        if (!passwordService.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        if (!passwordService.isBcrypt(user.getPassword())) {
            user.setPassword(passwordService.encode(password));
        }
        user.setLastLoginAt(java.time.LocalDateTime.now());
        return withCurrentStats(userRepository.save(user));
    }

    public boolean matchesPassword(User user, String rawPassword) {
        return user != null && passwordService.matches(rawPassword, user.getPassword());
    }

    public void updatePassword(Long userId, String newPassword) {
        User user = userRepository.findByIdAndDeleted(userId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPassword(passwordService.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void createRegisterBonusLog(User user, int amount) {
        if (user == null || user.getId() == null || amount <= 0) {
            return;
        }
        TransactionLog log = new TransactionLog();
        log.setUserId(user.getId());
        log.setChannelId(user.getChannelId());
        log.setType("IN");
        log.setAmount(amount);
        log.setRemark("新用户注册赠送算力，不可转赠");
        transactionLogRepository.save(log);
    }

    private void createAdminComputeAdjustmentLog(User user, int delta) {
        if (user == null || user.getId() == null || delta == 0) {
            return;
        }
        TransactionLog log = new TransactionLog();
        log.setUserId(user.getId());
        log.setChannelId(user.getChannelId());
        log.setType(delta > 0 ? "IN" : "CONSUME");
        log.setAmount(Math.abs(delta));
        log.setRemark(delta > 0 ? "管理员增加" : "管理员扣除");
        transactionLogRepository.save(log);
    }

    private Integer normalizeNonTransferableBalance(User user) {
        int balance = user.getComputeBalance() == null ? 0 : user.getComputeBalance();
        int nonTransferable = user.getNonTransferableComputeBalance() == null
                ? 0
                : user.getNonTransferableComputeBalance();
        return Math.max(0, Math.min(nonTransferable, balance));
    }

    private String normalizeApkChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "main";
        }
        return channel.trim();
    }

    private void normalizeUserChannel(User user) {
        if (user.getChannelId() != null) {
            channelRepository.findByIdAndDeleted(user.getChannelId(), ACTIVE).ifPresent(channel ->
                    user.setApkChannel(channel.getCode())
            );
            return;
        }
        String code = normalizeApkChannel(user.getApkChannel());
        Channel channel = channelRepository.findByCodeAndDeleted(code, ACTIVE)
                .orElseGet(() -> channelRepository.findByCodeAndDeleted(Channel.MAIN_CODE, ACTIVE)
                        .orElseThrow(() -> new RuntimeException("默认渠道不存在")));
        user.setChannelId(channel.getId());
        user.setApkChannel(channel.getCode());
    }

    private void validatePhoneUniqueness(User user) {
        String role = normalizeRole(user.getRole());
        if ("SUPER_ADMIN".equals(role) || "CHANNEL".equals(role)) {
            boolean duplicate = !userRepository.findAllByPhoneAndDeleted(user.getPhone(), ACTIVE).isEmpty();
            if (duplicate) {
                throw new RuntimeException("管理员手机号已存在");
            }
            return;
        }
        if (userRepository.existsByPhoneAndChannelIdAndDeleted(user.getPhone(), user.getChannelId(), ACTIVE)) {
            throw new RuntimeException("手机号已存在");
        }
    }

    private User resolveLoginUser(String phone, Long channelId) {
        List<User> samePhoneUsers = userRepository.findAllByPhoneAndDeleted(phone, ACTIVE);
        Optional<User> adminUser = samePhoneUsers.stream()
                .filter(user -> {
                    String role = normalizeRole(user.getRole());
                    return "SUPER_ADMIN".equals(role) || "CHANNEL".equals(role);
                })
                .findFirst();
        if (adminUser.isPresent()) {
            return adminUser.get();
        }
        if (channelId != null) {
            return userRepository.findFirstByPhoneAndChannelIdAndDeleted(phone, channelId, ACTIVE)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
        }
        return samePhoneUsers.stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        String normalized = role.trim().toUpperCase();
        return "ADMIN".equals(normalized) ? "SUPER_ADMIN" : normalized;
    }
}
