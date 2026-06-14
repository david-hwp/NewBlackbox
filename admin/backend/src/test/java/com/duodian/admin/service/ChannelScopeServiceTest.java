package com.duodian.admin.service;

import com.duodian.admin.entity.Channel;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelScopeServiceTest {
    private static final byte ACTIVE = 0;

    private final ChannelRepository channelRepository = mock(ChannelRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChannelScopeService service = new ChannelScopeService(channelRepository, userRepository);

    @Test
    void missingHeaderFallsBackToMainForOldApkCompatibility() {
        Channel main = channel(1L, "main");
        when(channelRepository.findByCodeAndDeleted("main", ACTIVE)).thenReturn(Optional.of(main));

        Channel resolved = service.resolveAppChannel(null, null);

        assertThat(resolved.getCode()).isEqualTo("main");
    }

    @Test
    void explicitUnknownHeaderDoesNotFallBackToMain() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ChannelScopeService.APK_CHANNEL_HEADER)).thenReturn("missing");
        when(channelRepository.findByCodeAndDeleted("missing", ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAppChannel(request, null))
                .hasMessage("渠道不存在");
    }

    @Test
    void explicitUnknownLegacyChannelDoesNotFallBackToMain() {
        when(channelRepository.findByCodeAndDeleted("missing", ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAppChannel(null, "missing"))
                .hasMessage("渠道不存在");
    }

    private Channel channel(Long id, String code) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setCode(code);
        channel.setName(code + "渠道");
        channel.setStatus(Channel.STATUS_ACTIVE);
        channel.setAppApplicationId("com.example." + code);
        channel.setEngineApplicationId("com.example." + code + ".engine");
        channel.setDeleted((byte) 0);
        return channel;
    }
}
