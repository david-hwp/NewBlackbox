package com.duodian.admin.service;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.config.CurrentPrincipal;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private static final byte ACTIVE = 0;

    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;

    public PermissionService(UserRepository userRepository, ChannelRepository channelRepository) {
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
    }

    public CurrentPrincipal currentPrincipal() {
        CurrentPrincipal principal = AuthContext.getPrincipal();
        if (principal == null || principal.getUserId() == null) {
            throw new RuntimeException("未登录");
        }
        if (principal.getRole() != null) {
            return principal;
        }
        User user = userRepository.findByIdAndDeleted(principal.getUserId(), ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Channel channel = user.getChannelId() == null
                ? null
                : channelRepository.findByIdAndDeleted(user.getChannelId(), ACTIVE).orElse(null);
        return new CurrentPrincipal(
                user.getId(),
                user.getRole(),
                user.getChannelId(),
                channel != null ? channel.getCode() : user.getApkChannel(),
                user.getApkChannel(),
                "db"
        );
    }

    public User currentUser() {
        return userRepository.findByIdAndDeleted(currentPrincipal().getUserId(), ACTIVE)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public void requireSuperAdmin() {
        if (!currentPrincipal().isSuperAdmin()) {
            throw new RuntimeException("无权限");
        }
    }

    public void requireAdminRole() {
        if (!currentPrincipal().isAdminRole()) {
            throw new RuntimeException("无权限");
        }
    }

    public void requireChannelAccess(Long channelId) {
        CurrentPrincipal principal = currentPrincipal();
        if (principal.isSuperAdmin()) {
            return;
        }
        if (!principal.isChannelAdmin() || channelId == null || !channelId.equals(principal.getChannelId())) {
            throw new RuntimeException("无权限访问该渠道");
        }
    }

    public Long filterChannelForQuery(Long requestedChannelId) {
        CurrentPrincipal principal = currentPrincipal();
        if (principal.isSuperAdmin()) {
            return requestedChannelId;
        }
        if (principal.isChannelAdmin()) {
            return principal.getChannelId();
        }
        throw new RuntimeException("无权限");
    }

    public void requireActiveChannelForMutation(Long channelId) {
        requireChannelAccess(channelId);
        Channel channel = channelRepository.findByIdAndDeleted(channelId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
        if (!channel.isActive()) {
            throw new RuntimeException("渠道已停用，当前仅允许查看");
        }
    }
}
