package com.duodian.admin.service;

import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ChannelService {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;

    public ChannelService(ChannelRepository channelRepository, UserRepository userRepository) {
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
    }

    public List<Channel> findAll() {
        return channelRepository.findByDeletedOrderByCreatedAtDesc(ACTIVE);
    }

    public Optional<Channel> findById(Long id) {
        return channelRepository.findByIdAndDeleted(id, ACTIVE);
    }

    public Optional<Channel> findByCode(String code) {
        return channelRepository.findByCodeAndDeleted(normalizeCode(code), ACTIVE);
    }

    public Channel mainChannel() {
        return findByCode(Channel.MAIN_CODE).orElseThrow(() -> new RuntimeException("默认渠道不存在"));
    }

    @Transactional
    public Channel create(Channel request) {
        Channel channel = new Channel();
        fillMutableFields(channel, request);
        channel.setCode(requireCode(request.getCode()));
        if (channelRepository.existsByCodeAndDeleted(channel.getCode(), ACTIVE)) {
            throw new RuntimeException("渠道标识已存在");
        }
        channel.setDeleted(ACTIVE);
        return channelRepository.save(channel);
    }

    @Transactional
    public Channel update(Long id, Channel request) {
        Channel existing = channelRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
        fillMutableFields(existing, request);
        return channelRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Channel existing = channelRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
        if (Channel.MAIN_CODE.equals(existing.getCode())) {
            throw new RuntimeException("默认渠道不能删除");
        }
        existing.setDeleted(DELETED);
        channelRepository.save(existing);
    }

    public List<User> searchAdminCandidates(String keyword) {
        return userRepository.searchChannelAdminCandidates(ACTIVE, normalize(keyword)).stream()
                .limit(50)
                .toList();
    }

    @Transactional
    public Channel bindAdmin(Long channelId, Long userId) {
        Channel channel = channelRepository.findByIdAndDeleted(channelId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
        User user = userRepository.findByIdAndDeleted(userId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        requireGlobalAdminPhoneUnique(user);
        user.setRole("CHANNEL");
        user.setChannelId(channel.getId());
        user.setApkChannel(channel.getCode());
        userRepository.save(user);
        channel.setAdminUserId(user.getId());
        return channelRepository.save(channel);
    }

    private void requireGlobalAdminPhoneUnique(User target) {
        String phone = normalize(target.getPhone());
        if (phone == null) {
            throw new RuntimeException("管理员手机号不能为空");
        }
        List<User> samePhoneUsers = userRepository.findAllByPhoneAndDeleted(phone, ACTIVE);
        boolean duplicatedAdmin = samePhoneUsers.stream()
                .filter(user -> !user.getId().equals(target.getId()))
                .map(User::getRole)
                .map(this::normalizeRole)
                .anyMatch(role -> "SUPER_ADMIN".equals(role) || "CHANNEL".equals(role));
        if (duplicatedAdmin) {
            throw new RuntimeException("管理员手机号已存在");
        }
    }

    private void fillMutableFields(Channel channel, Channel request) {
        channel.setName(request.getName());
        channel.setStatus(request.getStatus());
        channel.setAppDisplayName(request.getAppDisplayName());
        channel.setAppApplicationId(request.getAppApplicationId());
        channel.setAppIconFileName(request.getAppIconFileName());
        channel.setAppIconUrl(request.getAppIconUrl());
        channel.setAppIconChecksum(request.getAppIconChecksum());
        channel.setEngineDisplayName(request.getEngineDisplayName());
        channel.setEngineApplicationId(request.getEngineApplicationId());
        channel.setEngineIconFileName(request.getEngineIconFileName());
        channel.setEngineIconUrl(request.getEngineIconUrl());
        channel.setEngineIconChecksum(request.getEngineIconChecksum());
        channel.setEngineNotificationTitle(request.getEngineNotificationTitle());
        channel.setEngineNotificationText(request.getEngineNotificationText());
        channel.setRegisterBonusCompute(request.getRegisterBonusCompute());
        channel.setRemark(request.getRemark());
    }

    private String requireCode(String value) {
        String code = normalizeCode(value);
        if (code == null || !code.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new RuntimeException("渠道标识格式不正确");
        }
        return code;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return "ADMIN".equals(normalized) ? "SUPER_ADMIN" : normalized;
    }

    private String normalizeCode(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
