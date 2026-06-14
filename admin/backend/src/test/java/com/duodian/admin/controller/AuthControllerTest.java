package com.duodian.admin.controller;

import com.duodian.admin.config.JwtUtil;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.RegisterRequest;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ChannelScopeService;
import com.duodian.admin.service.SystemParameterService;
import com.duodian.admin.service.UserService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final UserService userService = mock(UserService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final ChannelScopeService channelScopeService = mock(ChannelScopeService.class);
    private final SystemParameterService systemParameterService = mock(SystemParameterService.class);
    private final AuthController controller = new AuthController(userService, jwtUtil, channelScopeService, systemParameterService);

    @Test
    void registerGiftsNonTransferableComputeSubscriptionAndDoesNotLogin() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setPassword("123456");
        request.setUsername("测试用户");

        User saved = new User();
        saved.setId(8L);
        saved.setPhone(request.getPhone());
        saved.setUsername(request.getUsername());
        saved.setComputeBalance(3);
        saved.setNonTransferableComputeBalance(3);
        Channel main = new Channel();
        main.setId(1L);
        main.setCode("main");
        main.setName("默认渠道");
        main.setStatus("ACTIVE");
        main.setRegisterBonusCompute(3);
        when(channelScopeService.resolveAppChannel(any(), any())).thenReturn(main);
        when(systemParameterService.intValue(
                main,
                SystemParameterService.REGISTER_TRIAL_SUBSCRIPTION_DAYS,
                30,
                0,
                3650
        )).thenReturn(45);
        when(userService.create(argThat(user ->
                user.getComputeBalance() == 3
                        && user.getNonTransferableComputeBalance() == 3
                        && UserService.PLAN_TRIAL.equals(user.getSubscriptionPlan())
                        && user.getSubscriptionExpiresAt() != null
                        && user.getPhone().equals("13800138000")
                        && user.getChannelId().equals(1L)
        ))).thenReturn(saved);

        ApiResponse<Void> response = controller.register(request, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("注册成功");
        assertThat(response.getData()).isNull();
        verify(userService).createRegisterBonusLog(saved, 3);
        verify(userService).createRegisterSubscriptionLog(saved, 45);
        verify(jwtUtil, never()).generateToken(any(), any());
    }
}
