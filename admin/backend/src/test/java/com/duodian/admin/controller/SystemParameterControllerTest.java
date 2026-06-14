package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.SystemParameter;
import com.duodian.admin.service.ChannelScopeService;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.SystemParameterService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemParameterControllerTest {
    private final SystemParameterService systemParameterService = mock(SystemParameterService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final ChannelScopeService channelScopeService = mock(ChannelScopeService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final SystemParameterController controller = new SystemParameterController(
            systemParameterService,
            permissionService,
            channelScopeService
    );

    @Test
    void appParametersResolveByRequestChannel() {
        Channel channel = new Channel();
        channel.setId(2L);
        channel.setCode("beta");
        when(channelScopeService.resolveAppChannel(request, null)).thenReturn(channel);
        when(systemParameterService.resolveAppParameters(channel)).thenReturn(Map.of(
                SystemParameterService.APP_MENU_GIFT_COMPUTE_LABEL,
                "赠送算力"
        ));

        ApiResponse<Map<String, String>> response = controller.appParameters(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry(SystemParameterService.APP_MENU_GIFT_COMPUTE_LABEL, "赠送算力");
        verify(channelScopeService).requireActiveForApp(channel);
    }

    @Test
    void updateRequiresExistingChannelPermission() {
        SystemParameter existing = new SystemParameter();
        existing.setId(9L);
        existing.setChannelId(3L);
        SystemParameter requestBody = new SystemParameter();
        requestBody.setChannelId(3L);
        requestBody.setName("参数");
        requestBody.setCode("x.y");
        requestBody.setValue("v");
        when(systemParameterService.findById(9L)).thenReturn(Optional.of(existing));
        when(permissionService.filterChannelForQuery(3L)).thenReturn(3L);
        when(systemParameterService.update(9L, requestBody)).thenReturn(requestBody);

        ApiResponse<SystemParameter> response = controller.update(9L, requestBody);

        assertThat(response.getCode()).isEqualTo(200);
        verify(permissionService).requireActiveChannelForMutation(3L);
        verify(systemParameterService).update(9L, requestBody);
    }
}
