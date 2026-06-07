package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.PlatformInfo;
import com.duodian.admin.entity.PlatformConfig;
import com.duodian.admin.repository.PlatformConfigRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platforms")
public class PlatformController {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final PlatformConfigRepository repository;

    public PlatformController(PlatformConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String platformId,
            @RequestParam(required = false) String packageName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null || hasText(name) || hasText(platformId) || hasText(packageName)) {
            Page<PlatformInfo> platforms = repository.searchPlatforms(
                    ACTIVE,
                    normalize(name),
                    normalize(platformId),
                    normalize(packageName),
                    PageRequest.of(
                            pageNumber(page) - 1,
                            pageSize(size),
                            Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id"))
                    )
            ).map(this::toInfo);
            return ApiResponse.success(PagedResponse.from(platforms));
        }
        return ApiResponse.success(repository.findByDeletedOrderBySortOrderAscIdAsc(ACTIVE).stream()
                .map(this::toInfo)
                .toList());
    }

    @PostMapping
    public ApiResponse<PlatformInfo> create(@RequestBody PlatformInfo request) {
        if (request.getId() == null || request.getId().isBlank()) {
            return ApiResponse.error("平台标识不能为空");
        }
        if (repository.existsByPlatformIdAndDeleted(request.getId().trim(), ACTIVE)) {
            return ApiResponse.error("平台标识已存在");
        }
        PlatformConfig config = new PlatformConfig();
        config.setDeleted(ACTIVE);
        fillConfig(config, request);
        return ApiResponse.success(toInfo(repository.save(config)));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlatformInfo> update(@PathVariable Long id, @RequestBody PlatformInfo request) {
        PlatformConfig config = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("平台不存在"));
        if (request.getId() == null || request.getId().isBlank()) {
            return ApiResponse.error("平台标识不能为空");
        }
        if (repository.existsByPlatformIdAndIdNotAndDeleted(request.getId().trim(), id, ACTIVE)) {
            return ApiResponse.error("平台标识已存在");
        }
        fillConfig(config, request);
        return ApiResponse.success(toInfo(repository.save(config)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        PlatformConfig config = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("平台不存在"));
        config.setDeleted(DELETED);
        repository.save(config);
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
