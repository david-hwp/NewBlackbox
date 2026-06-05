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
        ensureDefaults();
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

    private void ensureDefaults() {
        List<PlatformInfo> defaults = List.of(
                defaultPlatform("meituan", "美团外卖商家版", "com.sankuai.meituan.meituanwaimaibusiness", "/api/files/platform-icons/meituan.png", false, 10),
                defaultPlatform("taobao", "淘宝闪购", "com.taobao.qianniu", "/api/files/platform-icons/qianniu.png", false, 20),
                defaultPlatform("jd", "京东秒送", "com.jd.mrd.jingming", "/api/files/platform-icons/jd.png", true, 30),
                defaultPlatform("kuaishou", "快手团购", "com.kuaishou.nebula", "/api/files/platform-icons/kuaishou.png", false, 40),
                defaultPlatform("xiaohongshu", "小红书", "com.xingin.xhs", "/api/files/platform-icons/xiaohongshu.png", false, 50),
                defaultPlatform("ali", "阿里本地", "com.alipay.m.portal", "/api/files/platform-icons/koubei.png", false, 60)
        );
        defaults.forEach(item -> {
            PlatformConfig config = repository.findByPlatformId(item.getId())
                    .orElseGet(PlatformConfig::new);
            if (config.getId() == null || shouldSyncDefault(config, item)) {
                fillConfig(config, item);
                repository.save(config);
            }
        });
    }

    private boolean shouldSyncDefault(PlatformConfig config, PlatformInfo defaults) {
        return !equals(config.getName(), defaults.getName())
                || !equals(config.getPackageName(), defaults.getPackageName())
                || !equals(config.getIconUrl(), defaults.getIconUrl())
                || config.getSortOrder() == null
                || !config.getSortOrder().equals(defaults.getSortOrder());
    }

    private PlatformInfo defaultPlatform(String id, String name, String packageName, String iconUrl, boolean available, int sortOrder) {
        PlatformInfo info = new PlatformInfo(id, name, packageName, iconUrl, available);
        info.setSortOrder(sortOrder);
        return info;
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

    private boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
