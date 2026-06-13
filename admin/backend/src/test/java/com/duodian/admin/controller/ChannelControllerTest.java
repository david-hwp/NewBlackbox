package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.service.ChannelService;
import com.duodian.admin.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelControllerTest {
    private final ChannelService channelService = mock(ChannelService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final ChannelController controller = new ChannelController(channelService, permissionService);

    @Test
    void superAdminListReturnsAllChannels() {
        Channel main = channel(1L, "main");
        Channel beta = channel(2L, "beta");
        when(permissionService.filterChannelForQuery(null)).thenReturn(null);
        when(channelService.findAll()).thenReturn(List.of(main, beta));

        ApiResponse<List<Channel>> response = controller.list();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).extracting(Channel::getCode).containsExactly("main", "beta");
        verify(permissionService).requireAdminRole();
        verify(channelService).findAll();
    }

    @Test
    void channelAdminListOnlyReturnsOwnChannel() {
        Channel beta = channel(2L, "beta");
        when(permissionService.filterChannelForQuery(null)).thenReturn(2L);
        when(channelService.findById(2L)).thenReturn(Optional.of(beta));

        ApiResponse<List<Channel>> response = controller.list();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).extracting(Channel::getCode).containsExactly("beta");
        verify(permissionService).requireAdminRole();
        verify(channelService).findById(2L);
        verify(channelService, never()).findAll();
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
