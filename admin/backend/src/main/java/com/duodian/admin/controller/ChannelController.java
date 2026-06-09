package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ChannelService;
import com.duodian.admin.service.PermissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/channels")
public class ChannelController {

    private final ChannelService channelService;
    private final PermissionService permissionService;

    public ChannelController(ChannelService channelService, PermissionService permissionService) {
        this.channelService = channelService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<List<Channel>> list() {
        permissionService.requireAdminRole();
        return ApiResponse.success(channelService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Channel> get(@PathVariable Long id) {
        permissionService.requireAdminRole();
        return channelService.findById(id)
                .map(channel -> {
                    permissionService.requireChannelAccess(channel.getId());
                    return ApiResponse.success(channel);
                })
                .orElse(ApiResponse.error("渠道不存在"));
    }

    @PostMapping
    public ApiResponse<Channel> create(@RequestBody Channel request) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(channelService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Channel> update(@PathVariable Long id, @RequestBody Channel request) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(channelService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.requireSuperAdmin();
        channelService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/admin-candidates")
    public ApiResponse<List<User>> adminCandidates(@RequestParam(required = false) String keyword) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(channelService.searchAdminCandidates(keyword));
    }

    @PostMapping("/{id}/admin")
    public ApiResponse<Channel> bindAdmin(@PathVariable Long id, @RequestBody Map<String, Long> request) {
        permissionService.requireSuperAdmin();
        Long userId = request == null ? null : request.get("userId");
        if (userId == null) {
            return ApiResponse.error("用户ID不能为空");
        }
        return ApiResponse.success(channelService.bindAdmin(id, userId));
    }
}
