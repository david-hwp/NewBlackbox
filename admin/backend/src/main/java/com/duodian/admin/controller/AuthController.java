package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.config.JwtUtil;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.LoginRequest;
import com.duodian.admin.controller.dto.RegisterRequest;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ChannelScopeService;
import com.duodian.admin.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ChannelScopeService channelScopeService;

    public AuthController(UserService userService, JwtUtil jwtUtil, ChannelScopeService channelScopeService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.channelScopeService = channelScopeService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Channel channel = channelScopeService.resolveAppChannel(httpRequest, request.getApkChannel());
            User user = userService.login(request.getPhone(), request.getPassword(), channel.getId());
            if ("USER".equalsIgnoreCase(user.getRole())) {
                channelScopeService.requireActiveForApp(channel);
            }
            String token = jwtUtil.generateToken(
                    user.getId(),
                    user.getPhone(),
                    user.getRole(),
                    user.getChannelId(),
                    user.getApkChannel()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("token", token);

            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            Channel channel = channelScopeService.resolveAppChannel(httpRequest, request.getApkChannel());
            channelScopeService.requireActiveForApp(channel);
            int registerBonus = channel.getRegisterBonusCompute() == null ? 0 : channel.getRegisterBonusCompute();
            User user = new User();
            user.setPhone(request.getPhone());
            user.setPassword(request.getPassword());
            user.setUsername(request.getUsername());
            user.setChannelId(channel.getId());
            user.setApkChannel(channel.getCode());
            user.setComputeBalance(registerBonus);
            user.setNonTransferableComputeBalance(registerBonus);
            user.setShopCount(0);
            user.setPlatformCount(0);

            User saved = userService.create(user);
            userService.createRegisterBonusLog(saved, registerBonus);
            return new ApiResponse<>(200, "注册成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse<User> me() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        try {
            return ApiResponse.success(userService.refreshShopStats(userId));
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
