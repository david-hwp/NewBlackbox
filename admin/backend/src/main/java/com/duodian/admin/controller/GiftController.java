package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.ChannelComputeAllocateRequest;
import com.duodian.admin.controller.dto.GiftRequest;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ComputeService;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/compute")
public class GiftController {

    private final ComputeService computeService;
    private final UserService userService;
    private final PermissionService permissionService;

    public GiftController(ComputeService computeService, UserService userService, PermissionService permissionService) {
        this.computeService = computeService;
        this.userService = userService;
        this.permissionService = permissionService;
    }

    @PostMapping("/gift")
    public ApiResponse<Map<String, Object>> gift(@Valid @RequestBody GiftRequest request) {
        Long fromUserId = AuthContext.getUserId();
        if (fromUserId == null) {
            return ApiResponse.error(401, "未登录");
        }

        try {
            computeService.giftCompute(fromUserId, request.getToPhone(), request.getAmount());

            User fromUser = userService.findById(fromUserId).orElse(null);
            User toUser = fromUser == null
                    ? null
                    : userService.findByPhoneInChannel(request.getToPhone(), fromUser.getChannelId()).orElse(null);

            Map<String, Object> result = new HashMap<>();
            result.put("fromUser", fromUser != null ? fromUser.getPhone() : null);
            result.put("toUser", toUser != null ? toUser.getPhone() : null);
            result.put("amount", request.getAmount());
            result.put("fromBalance", fromUser != null ? fromUser.getComputeBalance() : 0);
            result.put("toBalance", toUser != null ? toUser.getComputeBalance() : 0);

            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/allocate")
    public ApiResponse<Map<String, Object>> allocate(@Valid @RequestBody ChannelComputeAllocateRequest request) {
        try {
            User currentUser = permissionService.currentUser();
            if (!"CHANNEL".equalsIgnoreCase(currentUser.getRole())) {
                return ApiResponse.error(403, "仅渠道管理员可以分配算力");
            }
            permissionService.requireActiveChannelForMutation(currentUser.getChannelId());
            ComputeService.AllocationResult allocation = computeService.allocateFromChannelAdmin(
                    currentUser.getId(),
                    request.getTargetUserId(),
                    request.getAmount()
            );
            Map<String, Object> result = new HashMap<>();
            result.put("channelAdminBalance", allocation.getChannelAdminBalance());
            result.put("targetBalance", allocation.getTargetBalance());
            result.put("outLogId", allocation.getOutLogId());
            result.put("inLogId", allocation.getInLogId());
            return ApiResponse.success(result);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
