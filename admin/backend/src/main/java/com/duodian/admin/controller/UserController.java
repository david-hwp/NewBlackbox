package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ChangePasswordRequest;
import com.duodian.admin.controller.dto.LoginRequest;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.SubscriptionUpdateRequest;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.UserRepository;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {
    private static final byte ACTIVE = 0;

    private final UserService userService;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public UserController(UserService userService, UserRepository userRepository, PermissionService permissionService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        permissionService.requireAdminRole();
        Long effectiveChannelId = permissionService.filterChannelForQuery(channelId);
        if (page != null || size != null || hasText(username) || hasText(phone) || hasText(role) || effectiveChannelId != null) {
            Page<User> users = userRepository.searchUsers(
                    ACTIVE,
                    normalize(username),
                    normalize(phone),
                    normalize(role),
                    effectiveChannelId,
                    PageRequest.of(pageNumber(page) - 1, pageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
            ).map(userService::withCurrentStats);
            return ApiResponse.success(PagedResponse.from(users));
        }
        return ApiResponse.success(userService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<User> get(@PathVariable Long id) {
        permissionService.requireAdminRole();
        return userService.findById(id)
                .filter(user -> {
                    permissionService.requireChannelAccess(user.getChannelId());
                    return true;
                })
                .map(userService::withCurrentStats)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("用户不存在"));
    }

    @PostMapping
    public ApiResponse<User> create(@RequestBody User user) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(userService.create(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable Long id, @RequestBody User user) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(userService.update(id, user));
    }

    @PutMapping("/{id}/subscription")
    public ApiResponse<User> updateSubscription(
            @PathVariable Long id,
            @RequestBody SubscriptionUpdateRequest request
    ) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(userService.updateSubscription(id, request == null ? null : request.getPlan()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.requireSuperAdmin();
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
        return ApiResponse.success(userService.updateProfile(userId, user));
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int pageNumber(Integer page) {
        return Math.max(1, page == null ? 1 : page);
    }

    private int pageSize(Integer size) {
        return Math.max(1, Math.min(100, size == null ? 10 : size));
    }
}
