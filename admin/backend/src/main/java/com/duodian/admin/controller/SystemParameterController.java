package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.SystemParameter;
import com.duodian.admin.service.ChannelScopeService;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.SystemParameterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system-parameters")
public class SystemParameterController {

    private final SystemParameterService systemParameterService;
    private final PermissionService permissionService;
    private final ChannelScopeService channelScopeService;

    public SystemParameterController(
            SystemParameterService systemParameterService,
            PermissionService permissionService,
            ChannelScopeService channelScopeService
    ) {
        this.systemParameterService = systemParameterService;
        this.permissionService = permissionService;
        this.channelScopeService = channelScopeService;
    }

    @GetMapping
    public ApiResponse<List<SystemParameter>> list(
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String keyword
    ) {
        permissionService.requireAdminRole();
        Long effectiveChannelId = permissionService.filterChannelForQuery(channelId);
        return ApiResponse.success(systemParameterService.findAll(effectiveChannelId, keyword));
    }

    @GetMapping("/app")
    public ApiResponse<Map<String, String>> appParameters(HttpServletRequest request) {
        Channel channel = channelScopeService.resolveAppChannel(request, null);
        channelScopeService.requireActiveForApp(channel);
        return ApiResponse.success(systemParameterService.resolveAppParameters(channel));
    }

    @PostMapping
    public ApiResponse<SystemParameter> create(@RequestBody SystemParameter parameter) {
        Long channelId = permissionService.filterChannelForQuery(parameter.getChannelId());
        if (channelId == null) {
            channelId = channelScopeService.mainChannel().getId();
        }
        permissionService.requireActiveChannelForMutation(channelId);
        parameter.setChannelId(channelId);
        return ApiResponse.success(systemParameterService.create(parameter));
    }

    @PutMapping("/{id}")
    public ApiResponse<SystemParameter> update(@PathVariable Long id, @RequestBody SystemParameter parameter) {
        SystemParameter existing = systemParameterService.findById(id)
                .orElseThrow(() -> new RuntimeException("系统参数不存在"));
        permissionService.requireActiveChannelForMutation(existing.getChannelId());
        Long requestedChannelId = permissionService.filterChannelForQuery(parameter.getChannelId());
        Long targetChannelId = requestedChannelId == null ? existing.getChannelId() : requestedChannelId;
        if (!targetChannelId.equals(existing.getChannelId())) {
            permissionService.requireActiveChannelForMutation(targetChannelId);
        }
        parameter.setChannelId(targetChannelId);
        return ApiResponse.success(systemParameterService.update(id, parameter));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SystemParameter existing = systemParameterService.findById(id)
                .orElseThrow(() -> new RuntimeException("系统参数不存在"));
        permissionService.requireActiveChannelForMutation(existing.getChannelId());
        systemParameterService.delete(id);
        return ApiResponse.success();
    }
}
