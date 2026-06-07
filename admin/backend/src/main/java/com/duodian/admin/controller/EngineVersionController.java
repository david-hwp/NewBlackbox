package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PackageVerifyRequest;
import com.duodian.admin.controller.dto.PackageVerifyResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.entity.EngineVersion;
import com.duodian.admin.repository.EngineVersionRepository;
import com.duodian.admin.service.PackageIntegrityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/engine-versions")
public class EngineVersionController {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final EngineVersionRepository repository;
    private final PackageIntegrityService packageIntegrityService;

    public EngineVersionController(EngineVersionRepository repository, PackageIntegrityService packageIntegrityService) {
        this.repository = repository;
        this.packageIntegrityService = packageIntegrityService;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Integer versionCode,
            @RequestParam(required = false) String versionName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null || versionCode != null || hasText(versionName)) {
            Page<EngineVersion> versions = repository.searchEngineVersions(
                    ACTIVE,
                    versionCode,
                    normalize(versionName),
                    available,
                    PageRequest.of(pageNumber(page) - 1, pageSize(size), Sort.by(Sort.Direction.DESC, "versionCode"))
            );
            return ApiResponse.success(PagedResponse.from(versions));
        }
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

    @PostMapping("/verify")
    public ApiResponse<PackageVerifyResponse> verify(@RequestBody PackageVerifyRequest request) {
        EngineVersion version = repository
                .findByVersionCodeAndAvailableAndDeleted(request == null ? null : request.getVersionCode(), true, ACTIVE)
                .orElse(null);
        if (version == null) {
            return ApiResponse.success(new PackageVerifyResponse(false));
        }
        boolean valid = packageIntegrityService.verify(
                "engine",
                version.getApkUrl(),
                version.getVersionCode(),
                version.getChecksum(),
                request
        );
        return ApiResponse.success(new PackageVerifyResponse(valid));
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
        return Math.max(1, Math.min(100, size == null ? 20 : size));
    }
}
