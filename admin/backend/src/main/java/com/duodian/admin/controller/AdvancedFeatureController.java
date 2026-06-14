package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.AdvancedFeatureResponse;
import com.duodian.admin.controller.dto.AdvancedFeatureUpdateRequest;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.service.AdvancedFeatureService;
import com.duodian.admin.service.PermissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/advanced-features")
public class AdvancedFeatureController {
    private final AdvancedFeatureService advancedFeatureService;
    private final PermissionService permissionService;

    public AdvancedFeatureController(
            AdvancedFeatureService advancedFeatureService,
            PermissionService permissionService
    ) {
        this.advancedFeatureService = advancedFeatureService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<List<AdvancedFeatureResponse>> list() {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(advancedFeatureService.findAll());
    }

    @GetMapping("/app")
    public ApiResponse<List<AdvancedFeatureResponse>> appList() {
        return ApiResponse.success(advancedFeatureService.findAll());
    }

    @PutMapping("/{code}")
    public ApiResponse<AdvancedFeatureResponse> update(
            @PathVariable String code,
            @RequestBody AdvancedFeatureUpdateRequest request
    ) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(advancedFeatureService.update(code, request));
    }
}
