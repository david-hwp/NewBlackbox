package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.EngineVersion;
import com.duodian.admin.repository.EngineVersionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/engine-versions")
public class EngineVersionController {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final EngineVersionRepository repository;

    public EngineVersionController(EngineVersionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<EngineVersion>> list(@RequestParam(required = false) Boolean available) {
        if (available != null) {
            return ApiResponse.success(repository.findByAvailableAndDeletedOrderByVersionCodeDesc(available, ACTIVE));
        }
        return ApiResponse.success(repository.findByDeletedOrderByVersionCodeDesc(ACTIVE));
    }

    @PostMapping
    public ApiResponse<EngineVersion> create(@RequestBody EngineVersion version) {
        version.setDeleted(ACTIVE);
        return ApiResponse.success(repository.save(version));
    }

    @PutMapping("/{id}")
    public ApiResponse<EngineVersion> update(@PathVariable Long id, @RequestBody EngineVersion version) {
        EngineVersion existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("引擎版本不存在"));
        existing.setVersionCode(version.getVersionCode());
        existing.setVersionName(version.getVersionName());
        existing.setApkUrl(version.getApkUrl());
        existing.setChecksum(version.getChecksum());
        existing.setChangelog(version.getChangelog());
        existing.setAvailable(version.getAvailable());
        return ApiResponse.success(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        EngineVersion existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("引擎版本不存在"));
        existing.setDeleted(DELETED);
        repository.save(existing);
        return ApiResponse.success();
    }
}
