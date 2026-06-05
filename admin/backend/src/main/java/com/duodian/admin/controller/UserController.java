package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ChangePasswordRequest;
import com.duodian.admin.controller.dto.LoginRequest;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<User>> list() {
        return ApiResponse.success(userService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<User> get(@PathVariable Long id) {
        return userService.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("用户不存在"));
    }

    @PostMapping
    public ApiResponse<User> create(@RequestBody User user) {
        return ApiResponse.success(userService.create(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable Long id, @RequestBody User user) {
        return ApiResponse.success(userService.update(id, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.success();
    }

    /**
     * @deprecated 请使用 POST /auth/login
     */
    @Deprecated
    @PostMapping("/login")
    public ApiResponse<User> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userService.login(request.getPhone(), request.getPassword());
            return ApiResponse.success(user);
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PutMapping("/me/username")
    public ApiResponse<User> updateMyUsername(@RequestBody Map<String, String> request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        String username = request.get("username");
        if (username == null || username.isBlank() || username.length() > 32) {
            return ApiResponse.error("用户名不能为空且不能超过32字符");
        }
        String avatarUrl = request.get("avatarUrl");
        if (avatarUrl != null && avatarUrl.length() > 512) {
            return ApiResponse.error("头像地址不能超过512字符");
        }
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setUsername(username.trim());
        if (request.containsKey("avatarUrl")) {
            user.setAvatarUrl(avatarUrl == null || avatarUrl.isBlank() ? null : avatarUrl.trim());
        }
        return ApiResponse.success(userService.update(userId, user));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> updateMyPassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ApiResponse.error("两次输入的新密码不一致");
        }
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!userService.matchesPassword(user, request.getOldPassword())) {
            return ApiResponse.error(400, "原密码错误");
        }
        userService.updatePassword(userId, request.getNewPassword());
        return ApiResponse.success();
    }
}
