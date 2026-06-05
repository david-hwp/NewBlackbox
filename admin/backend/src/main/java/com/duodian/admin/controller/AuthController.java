package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.config.JwtUtil;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.LoginRequest;
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
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String password = request.get("password");
        String username = request.get("username");

        if (phone == null || phone.isBlank()) {
            return ApiResponse.error("手机号不能为空");
        }
        if (password == null || password.length() < 6) {
            return ApiResponse.error("密码至少6位");
        }
        if (username == null || username.isBlank() || username.length() > 32) {
            return ApiResponse.error("用户名不能为空且不能超过32字符");
        }

        try {
            User user = new User();
            user.setPhone(phone);
            user.setPassword(password);
            user.setUsername(username);
            user.setComputeBalance(0);
            user.setShopCount(0);
            user.setPlatformCount(0);

            User saved = userService.create(user);
            String token = jwtUtil.generateToken(saved.getId(), saved.getPhone());

            Map<String, Object> result = new HashMap<>();
            result.put("user", saved);
            result.put("token", token);

            return ApiResponse.success(result);
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
        return userService.findById(userId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("用户不存在"));
    }
}
