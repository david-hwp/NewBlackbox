package com.duodian.admin.service;

import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.SystemParameter;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.SystemParameterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemParameterService {
    public static final String REGISTER_TRIAL_SUBSCRIPTION_DAYS = "register.trial.subscription.days";
    public static final String APP_MENU_GIFT_COMPUTE_LABEL = "app.menu.gift_compute.label";
    public static final String APP_MENU_RECLAIM_COMPUTE_LABEL = "app.menu.reclaim_compute.label";
    public static final String APP_MENU_GIFT_PHONE_MINUTES_LABEL = "app.menu.gift_phone_minutes.label";
    public static final String APP_MENU_RECLAIM_PHONE_MINUTES_LABEL = "app.menu.reclaim_phone_minutes.label";
    public static final String APP_MENU_TRANSACTION_LOGS_LABEL = "app.menu.transaction_logs.label";
    public static final String APP_SHOP_FEATURE_BAD_REVIEW_LOCATION_LABEL = "app.shop_feature.bad_review_location.label";
    public static final String APP_SHOP_FEATURE_BUSINESS_REPORT_LABEL = "app.shop_feature.business_report.label";
    public static final String APP_SHOP_FEATURE_OUTBOUND_PRAISE_LABEL = "app.shop_feature.outbound_praise.label";
    public static final String APP_SHOP_FEATURE_REVIEW_APPEAL_LABEL = "app.shop_feature.review_appeal.label";
    public static final String APP_SHOP_FEATURE_PRIVATE_TRAFFIC_LABEL = "app.shop_feature.private_traffic.label";

    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final SystemParameterRepository repository;
    private final ChannelRepository channelRepository;
    private final ChannelScopeService channelScopeService;

    public SystemParameterService(
            SystemParameterRepository repository,
            ChannelRepository channelRepository,
            ChannelScopeService channelScopeService
    ) {
        this.repository = repository;
        this.channelRepository = channelRepository;
        this.channelScopeService = channelScopeService;
    }

    public List<SystemParameter> findAll(Long channelId, String keyword) {
        List<SystemParameter> parameters = channelId == null
                ? repository.findByDeletedOrderByUpdatedAtDesc(ACTIVE)
                : repository.findByChannelIdAndDeletedOrderByUpdatedAtDesc(channelId, ACTIVE);
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword == null) {
            return parameters;
        }
        String lowerKeyword = normalizedKeyword.toLowerCase(Locale.ROOT);
        return parameters.stream()
                .filter(parameter -> contains(parameter.getName(), lowerKeyword)
                        || contains(parameter.getCode(), lowerKeyword)
                        || contains(parameter.getDescription(), lowerKeyword))
                .toList();
    }

    public Optional<SystemParameter> findById(Long id) {
        return repository.findByIdAndDeleted(id, ACTIVE);
    }

    @Transactional
    public SystemParameter create(SystemParameter parameter) {
        Long channelId = requireChannel(parameter.getChannelId());
        String code = requireCode(parameter.getCode());
        if (repository.existsByChannelIdAndCodeAndDeleted(channelId, code, ACTIVE)) {
            throw new RuntimeException("同渠道参数编码已存在");
        }
        parameter.setChannelId(channelId);
        parameter.setCode(code);
        parameter.setBuiltin((byte) 0);
        normalizeParameter(parameter);
        return repository.save(parameter);
    }

    @Transactional
    public SystemParameter update(Long id, SystemParameter parameter) {
        SystemParameter existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("系统参数不存在"));
        Long requestedChannelId = parameter.getChannelId() == null ? existing.getChannelId() : parameter.getChannelId();
        Long channelId = requireChannel(requestedChannelId);
        String code = parameter.getCode() == null ? existing.getCode() : requireCode(parameter.getCode());
        repository.findFirstByChannelIdAndCodeAndDeleted(channelId, code, ACTIVE)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new RuntimeException("同渠道参数编码已存在");
                });
        existing.setChannelId(channelId);
        existing.setCode(code);
        if (parameter.getName() != null) {
            existing.setName(parameter.getName());
        }
        if (parameter.getValue() != null) {
            existing.setValue(parameter.getValue());
        }
        existing.setDescription(parameter.getDescription());
        normalizeParameter(existing);
        return repository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        SystemParameter existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("系统参数不存在"));
        if (existing.getBuiltin() != null && existing.getBuiltin() == 1) {
            throw new RuntimeException("系统内置参数不能删除");
        }
        existing.setDeleted(DELETED);
        repository.save(existing);
    }

    public Map<String, String> resolveAppParameters(Channel channel) {
        Long mainChannelId = channelScopeService.mainChannel().getId();
        Long targetChannelId = channel == null ? mainChannelId : channel.getId();
        Map<String, String> resolved = new LinkedHashMap<>();
        repository.findByChannelIdAndDeletedOrderByUpdatedAtDesc(mainChannelId, ACTIVE)
                .forEach(parameter -> resolved.put(parameter.getCode(), parameter.getValue()));
        if (!targetChannelId.equals(mainChannelId)) {
            repository.findByChannelIdAndDeletedOrderByUpdatedAtDesc(targetChannelId, ACTIVE)
                    .forEach(parameter -> resolved.put(parameter.getCode(), parameter.getValue()));
        }
        return resolved;
    }

    public int intValue(Channel channel, String code, int fallback, int min, int max) {
        String value = resolveAppParameters(channel).get(code);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void normalizeParameter(SystemParameter parameter) {
        parameter.setName(requireText(parameter.getName(), "中文名称不能为空"));
        parameter.setValue(parameter.getValue() == null ? "" : parameter.getValue().trim());
    }

    private Long requireChannel(Long channelId) {
        Long effectiveChannelId = channelId == null ? channelScopeService.mainChannel().getId() : channelId;
        channelRepository.findByIdAndDeleted(effectiveChannelId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
        return effectiveChannelId;
    }

    private String requireCode(String code) {
        String normalized = normalize(code);
        if (normalized == null) {
            throw new RuntimeException("参数编码不能为空");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
