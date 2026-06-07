package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.AppVersion;
import com.duodian.admin.repository.AppVersionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app-versions")
public class AppVersionController {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final AppVersionRepository repository;

    public AppVersionController(AppVersionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<AppVersion>> list(@RequestParam(required = false) Boolean published) {
        if (published != null) {
            return ApiResponse.success(repository.findByPublishedAndDeletedOrderByVersionCodeDesc(published, ACTIVE));
        }
        return ApiResponse.success(repository.findByDeletedOrderByVersionCodeDesc(ACTIVE));
    }

    @PostMapping
    public ApiResponse<AppVersion> create(@RequestBody AppVersion version) {
        version.setDeleted(ACTIVE);
        return ApiResponse.success(repository.save(version));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppVersion> update(@PathVariable Long id, @RequestBody AppVersion version) {
        AppVersion existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("主APK版本不存在"));
        existing.setVersionCode(version.getVersionCode());
        existing.setVersionName(version.getVersionName());
        existing.setApkUrl(version.getApkUrl());
        existing.setChecksum(version.getChecksum());
        existing.setFileSize(version.getFileSize());
        existing.setChangelog(version.getChangelog());
        existing.setPublished(version.getPublished());
        return ApiResponse.success(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        AppVersion existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("主APK版本不存在"));
        existing.setDeleted(DELETED);
        repository.save(existing);
        return ApiResponse.success();
    }
}
