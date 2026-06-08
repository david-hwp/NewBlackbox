package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.config.JwtUtil;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.LoginRequest;
import com.duodian.admin.controller.dto.RegisterRequest;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userService.login(request.getPhone(), request.getPassword());
            String token = jwtUtil.generateToken(user.getId(), user.getPhone());

            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("token", token);

            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = new User();
            user.setPhone(request.getPhone());
            user.setPassword(request.getPassword());
            user.setUsername(request.getUsername());
            user.setApkChannel(request.getApkChannel());
            user.setComputeBalance(3);
            user.setNonTransferableComputeBalance(3);
            user.setShopCount(0);
            user.setPlatformCount(0);

            User saved = userService.create(user);
            userService.createRegisterBonusLog(saved, 3);
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
