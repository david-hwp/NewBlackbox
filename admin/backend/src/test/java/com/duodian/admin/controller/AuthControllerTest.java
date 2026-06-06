package com.duodian.admin.controller;

import com.duodian.admin.config.JwtUtil;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.RegisterRequest;
import com.duodian.admin.entity.User;
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
    private final AuthController controller = new AuthController(userService, jwtUtil);

    @Test
    void registerGiftsNonTransferableComputeAndDoesNotLogin() {
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
        when(userService.create(argThat(user ->
                user.getComputeBalance() == 3
                        && user.getNonTransferableComputeBalance() == 3
                        && user.getPhone().equals("13800138000")
        ))).thenReturn(saved);

        ApiResponse<Void> response = controller.register(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("注册成功");
        assertThat(response.getData()).isNull();
        verify(userService).createRegisterBonusLog(saved, 3);
        verify(jwtUtil, never()).generateToken(any(), any());
    }
}
