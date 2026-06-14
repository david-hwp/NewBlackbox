package com.duodian.admin.service;

import com.duodian.admin.controller.dto.AdvancedFeatureResponse;
import com.duodian.admin.controller.dto.AdvancedFeatureUpdateRequest;
import com.duodian.admin.entity.AdvancedFeature;
import com.duodian.admin.repository.AdvancedFeatureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class AdvancedFeatureService {
    public static final String BAD_REVIEW_LOCATION = "bad_review_location";
    public static final String BUSINESS_REPORT = "business_report";
    public static final String OUTBOUND_PRAISE = "outbound_praise";
    public static final String REVIEW_APPEAL = "review_appeal";
    public static final String PRIVATE_TRAFFIC = "private_traffic";

    private static final byte ACTIVE = 0;

    private final AdvancedFeatureRepository repository;

    public AdvancedFeatureService(AdvancedFeatureRepository repository) {
        this.repository = repository;
    }

    public List<AdvancedFeatureResponse> findAll() {
        return repository.findByDeletedOrderBySortOrderAscIdAsc(ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdvancedFeatureResponse update(String code, AdvancedFeatureUpdateRequest request) {
        AdvancedFeature feature = repository.findByCodeAndDeleted(normalizeRequired(code, "功能编码不能为空"), ACTIVE)
                .orElseThrow(() -> new RuntimeException("高级功能不存在"));
        feature.setOnline(Boolean.TRUE.equals(request.getOnline()));
        feature.setMonthlyComputeCost(clampCost(request.getMonthlyComputeCost()));
        feature.setSupportedPlatformPackages(joinPackages(request.getSupportedPlatformPackages()));
        feature.setTitle(requireText(request.getTitle(), "标题不能为空"));
        feature.setLine1(defaultText(request.getLine1()));
        feature.setLine2(defaultText(request.getLine2()));
        feature.setOutboundEnabled(Boolean.TRUE.equals(request.getOutboundEnabled()));
        return toResponse(repository.save(feature));
    }

    private AdvancedFeatureResponse toResponse(AdvancedFeature feature) {
        AdvancedFeatureResponse response = new AdvancedFeatureResponse();
        response.setId(feature.getId());
        response.setCode(feature.getCode());
        response.setName(feature.getName());
        response.setOnline(Boolean.TRUE.equals(feature.getOnline()));
        response.setMonthlyComputeCost(feature.getMonthlyComputeCost() == null ? 0 : feature.getMonthlyComputeCost());
        response.setSupportedPlatformPackages(splitPackages(feature.getSupportedPlatformPackages()));
        response.setTitle(feature.getTitle());
        response.setLine1(feature.getLine1());
        response.setLine2(feature.getLine2());
        response.setTitleCode(feature.getTitleCode());
        response.setLine1Code(feature.getLine1Code());
        response.setLine2Code(feature.getLine2Code());
        response.setOutboundEnabled(Boolean.TRUE.equals(feature.getOutboundEnabled()));
        response.setSortOrder(feature.getSortOrder());
        response.setUpdatedAt(feature.getUpdatedAt());
        return response;
    }

    private List<String> splitPackages(String packages) {
        String normalized = normalize(packages);
        if (normalized == null) {
            return List.of();
        }
        return Arrays.stream(normalized.split(","))
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String joinPackages(List<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return null;
        }
        String joined = packages.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        if (joined != null && joined.length() > 2048) {
            throw new RuntimeException("支持平台配置过长");
        }
        return joined;
    }

    private int clampCost(Integer cost) {
        return Math.max(0, Math.min(9999, cost == null ? 0 : cost));
    }

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String defaultText(String value) {
        String normalized = normalize(value);
        return normalized == null ? "-" : normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
