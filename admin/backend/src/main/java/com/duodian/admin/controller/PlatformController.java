package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PlatformInfo;
import com.duodian.admin.entity.PlatformConfig;
import com.duodian.admin.repository.PlatformConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platforms")
public class PlatformController {

    private final PlatformConfigRepository repository;

    public PlatformController(PlatformConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<PlatformInfo>> list() {
        return ApiResponse.success(repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toInfo)
                .toList());
    }

    @PostMapping
    public ApiResponse<PlatformInfo> create(@RequestBody PlatformInfo request) {
        if (request.getId() == null || request.getId().isBlank()) {
            return ApiResponse.error("平台标识不能为空");
        }
        if (repository.existsByPlatformId(request.getId().trim())) {
            return ApiResponse.error("平台标识已存在");
        }
        PlatformConfig config = new PlatformConfig();
        fillConfig(config, request);
        return ApiResponse.success(toInfo(repository.save(config)));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlatformInfo> update(@PathVariable Long id, @RequestBody PlatformInfo request) {
        PlatformConfig config = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("平台不存在"));
        if (request.getId() == null || request.getId().isBlank()) {
            return ApiResponse.error("平台标识不能为空");
        }
        if (repository.existsByPlatformIdAndIdNot(request.getId().trim(), id)) {
            return ApiResponse.error("平台标识已存在");
        }
        fillConfig(config, request);
        return ApiResponse.success(toInfo(repository.save(config)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ApiResponse.success();
    }

    private void fillConfig(PlatformConfig config, PlatformInfo request) {
        config.setPlatformId(request.getId());
        config.setName(request.getName());
        config.setPackageName(request.getPackageName());
        config.setIconUrl(request.getIconUrl());
        config.setAvailable(request.isAvailable());
        config.setSortOrder(request.getSortOrder());
    }

    private PlatformInfo toInfo(PlatformConfig config) {
        PlatformInfo info = new PlatformInfo(
                config.getPlatformId(),
                config.getName(),
                config.getPackageName(),
                config.getIconUrl(),
                Boolean.TRUE.equals(config.getAvailable())
        );
        info.setDbId(config.getId());
        info.setSortOrder(config.getSortOrder());
        return info;
    }
}
