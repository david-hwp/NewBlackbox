package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.GiftRequest;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.ComputeService;
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

    public GiftController(ComputeService computeService, UserService userService) {
        this.computeService = computeService;
        this.userService = userService;
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
            User toUser = userService.findByPhone(request.getToPhone()).orElse(null);

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
}
