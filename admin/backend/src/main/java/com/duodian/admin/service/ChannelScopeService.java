package com.duodian.admin.service;

import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ChannelScopeService {
    public static final String APK_CHANNEL_HEADER = "X-Apk-Channel";
    private static final byte ACTIVE = 0;

    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;

    public ChannelScopeService(ChannelRepository channelRepository, UserRepository userRepository) {
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
    }

    public Channel resolveAppChannel(HttpServletRequest request, String legacyApkChannel) {
        String headerCode = normalize(request == null ? null : request.getHeader(APK_CHANNEL_HEADER));
        String code = headerCode;
        if (code == null) {
            code = normalize(legacyApkChannel);
        }
        if (code == null) {
            code = Channel.MAIN_CODE;
        }
        return channelRepository.findByCodeAndDeleted(code, ACTIVE)
                .orElseThrow(() -> new RuntimeException(headerCode == null && normalize(legacyApkChannel) == null
                        ? "默认渠道不存在"
                        : "渠道不存在"));
    }

    public boolean hasAppChannelHeader(HttpServletRequest request) {
        return normalize(request == null ? null : request.getHeader(APK_CHANNEL_HEADER)) != null;
    }

    public Channel mainChannel() {
        return channelRepository.findByCodeAndDeleted(Channel.MAIN_CODE, ACTIVE)
                .orElseThrow(() -> new RuntimeException("默认渠道不存在"));
    }

    public Channel findChannel(Long channelId) {
        if (channelId == null) {
            return mainChannel();
        }
        return channelRepository.findByIdAndDeleted(channelId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
    }

    public void requireActiveForApp(Channel channel) {
        if (channel != null && channel.isActive()) {
            return;
        }
        String phone = "xxx";
        if (channel != null && channel.getAdminUserId() != null) {
            phone = userRepository.findByIdAndDeleted(channel.getAdminUserId(), ACTIVE)
                    .map(User::getPhone)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse("xxx");
        }
        throw new RuntimeException("该产品暂不可用，请联系：" + phone + "（渠道管理员的手机号）");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
