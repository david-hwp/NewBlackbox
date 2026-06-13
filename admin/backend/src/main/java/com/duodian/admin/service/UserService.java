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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;
    public static final String PLAN_NONE = "NONE";
    public static final String PLAN_TRIAL = "TRIAL";
    public static final String PLAN_MONTHLY = "MONTHLY";
    public static final String PLAN_QUARTERLY = "QUARTERLY";
    public static final String PLAN_YEARLY = "YEARLY";

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
        normalizeSubscriptionState(user);
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
        existing.setRole(normalizeRole(user.getRole()));
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

    @Transactional
    public void createRegisterSubscriptionLog(User user, int days) {
        if (user == null || user.getId() == null || days <= 0) {
            return;
        }
        TransactionLog log = new TransactionLog();
        log.setUserId(user.getId());
        log.setChannelId(user.getChannelId());
        log.setType("IN");
        log.setAmount(0);
        log.setRemark("新用户注册赠送订阅体验 " + days + " 天");
        transactionLogRepository.save(log);
    }

    @Transactional
    public User updateSubscription(Long id, String plan) {
        User existing = userRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String normalizedPlan = normalizeAdminSubscriptionPlan(plan);
        LocalDateTime now = LocalDateTime.now();
        if (PLAN_NONE.equals(normalizedPlan)) {
            existing.setSubscriptionPlan(PLAN_NONE);
            existing.setSubscriptionExpiresAt(null);
        } else {
            existing.setSubscriptionPlan(normalizedPlan);
            existing.setSubscriptionExpiresAt(expireAtForPlan(normalizedPlan, now));
        }
        existing.setSubscriptionUpdatedAt(now);
        User saved = userRepository.save(withCurrentStats(existing));
        createAdminSubscriptionAdjustmentLog(saved, normalizedPlan);
        return saved;
    }

    public boolean hasActiveSubscription(Long userId) {
        return userRepository.findByIdAndDeleted(userId, ACTIVE)
                .map(User::isSubscriptionActive)
                .orElse(false);
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

    private void createAdminSubscriptionAdjustmentLog(User user, String plan) {
        if (user == null || user.getId() == null) {
            return;
        }
        TransactionLog log = new TransactionLog();
        log.setUserId(user.getId());
        log.setChannelId(user.getChannelId());
        log.setType("IN");
        log.setAmount(0);
        log.setRemark(PLAN_NONE.equals(plan)
                ? "管理员关闭订阅"
                : "管理员开通订阅: " + displaySubscriptionPlan(plan));
        transactionLogRepository.save(log);
    }

    private Integer normalizeNonTransferableBalance(User user) {
        int balance = user.getComputeBalance() == null ? 0 : user.getComputeBalance();
        int nonTransferable = user.getNonTransferableComputeBalance() == null
                ? 0
                : user.getNonTransferableComputeBalance();
        return Math.max(0, Math.min(nonTransferable, balance));
    }

    private void normalizeSubscriptionState(User user) {
        if (user == null) {
            return;
        }
        String plan = normalizeSubscriptionPlan(user.getSubscriptionPlan());
        user.setSubscriptionPlan(plan);
        if (PLAN_NONE.equals(plan)) {
            user.setSubscriptionExpiresAt(null);
        }
        if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionUpdatedAt() == null) {
            user.setSubscriptionUpdatedAt(LocalDateTime.now());
        }
    }

    public String normalizeSubscriptionPlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return PLAN_NONE;
        }
        String normalized = plan.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case PLAN_TRIAL, PLAN_MONTHLY, PLAN_QUARTERLY, PLAN_YEARLY -> normalized;
            default -> PLAN_NONE;
        };
    }

    private String normalizeAdminSubscriptionPlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return PLAN_NONE;
        }
        String normalized = plan.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case PLAN_NONE, PLAN_MONTHLY, PLAN_QUARTERLY, PLAN_YEARLY -> normalized;
            default -> throw new RuntimeException("订阅套餐无效");
        };
    }

    private LocalDateTime expireAtForPlan(String plan, LocalDateTime now) {
        return switch (plan) {
            case PLAN_MONTHLY -> now.plusMonths(1);
            case PLAN_QUARTERLY -> now.plusMonths(3);
            case PLAN_YEARLY -> now.plusYears(1);
            case PLAN_TRIAL -> now.plusDays(30);
            default -> null;
        };
    }

    private String displaySubscriptionPlan(String plan) {
        return switch (plan) {
            case PLAN_TRIAL -> "新用户体验";
            case PLAN_MONTHLY -> "月度";
            case PLAN_QUARTERLY -> "季度";
            case PLAN_YEARLY -> "年度";
            default -> "无订阅";
        };
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
