package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.TransactionLogResponse;
import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.TransactionLogService;
import com.duodian.admin.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/logs")
public class TransactionLogController {
    private static final byte ACTIVE = 0;

    private final TransactionLogService logService;
    private final TransactionLogRepository logRepository;
    private final UserService userService;
    private final PermissionService permissionService;

    public TransactionLogController(
            TransactionLogService logService,
            TransactionLogRepository logRepository,
            UserService userService,
            PermissionService permissionService
    ) {
        this.logService = logService;
        this.logRepository = logRepository;
        this.userService = userService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        permissionService.requireAdminRole();
        Long effectiveChannelId = permissionService.filterChannelForQuery(channelId);
        if (page != null || size != null || hasText(phone) || hasText(shopName) || effectiveChannelId != null) {
            Page<TransactionLogResponse> logs = logRepository.searchLogs(
                    ACTIVE,
                    userId,
                    effectiveChannelId,
                    normalize(type),
                    normalize(phone),
                    normalize(shopName),
                    PageRequest.of(pageNumber(page) - 1, pageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
            ).map(this::toResponse);
            return ApiResponse.success(PagedResponse.from(logs));
        }
        List<TransactionLog> logs;
        if (effectiveChannelId != null) {
            logs = logService.findByChannelId(effectiveChannelId);
        } else if (userId != null) {
            logs = logService.findByUserId(userId);
        } else if (type != null) {
            logs = logService.findByType(type);
        } else {
            logs = logService.findAll();
        }
        return ApiResponse.success(logs.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionLog> get(@PathVariable Long id) {
        permissionService.requireAdminRole();
        return logService.findById(id)
                .filter(log -> {
                    permissionService.requireChannelAccess(log.getChannelId());
                    return true;
                })
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("日志不存在"));
    }

    @PostMapping
    public ApiResponse<TransactionLog> create(@RequestBody TransactionLog log) {
        permissionService.requireSuperAdmin();
        return ApiResponse.success(logService.create(log));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.requireSuperAdmin();
        logService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/my")
    public ApiResponse<Map<String, Object>> myLogs(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        LocalDateTime start = null;
        LocalDateTime end = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (startDate != null && !startDate.isBlank()) {
            start = LocalDate.parse(startDate, formatter).atStartOfDay();
        }
        if (endDate != null && !endDate.isBlank()) {
            end = LocalDate.parse(endDate, formatter).atTime(23, 59, 59);
        }

        Page<TransactionLog> result = logService.findByUserIdAndConditions(userId, type, start, end, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());
        response.put("list", result.getContent());
        response.put("page", result.getNumber() + 1);
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("total", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());

        return ApiResponse.success(response);
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
        return Math.max(1, Math.min(100, size == null ? 10 : size));
    }

    private TransactionLogResponse toResponse(TransactionLog log) {
        return TransactionLogResponse.from(
                log,
                log.getUserId() != null ? userService.findById(log.getUserId()).orElse(null) : null
        );
    }
}
