package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.AuthorizedShopOrderCrawlTarget;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.ShopOrderIngestRequest;
import com.duodian.admin.controller.dto.ShopOrderIngestResponse;
import com.duodian.admin.controller.dto.ShopOrderResponse;
import com.duodian.admin.service.ExternalCallbackTokenService;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.ShopOrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/shop-orders")
public class ShopOrderController {
    private final ShopOrderService shopOrderService;
    private final PermissionService permissionService;
    private final ExternalCallbackTokenService externalCallbackTokenService;

    public ShopOrderController(
            ShopOrderService shopOrderService,
            PermissionService permissionService,
            ExternalCallbackTokenService externalCallbackTokenService
    ) {
        this.shopOrderService = shopOrderService;
        this.permissionService = permissionService;
        this.externalCallbackTokenService = externalCallbackTokenService;
    }

    @GetMapping
    public ApiResponse<PagedResponse<ShopOrderResponse>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) String customerKeyword,
            @RequestParam(required = false) String orderKeyword,
            @RequestParam(required = false) String completedStart,
            @RequestParam(required = false) String completedEnd,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        permissionService.requireSuperAdmin();
        try {
            Page<ShopOrderResponse> orders = shopOrderService.search(
                    shopId,
                    platform,
                    status,
                    shopName,
                    customerKeyword,
                    orderKeyword,
                    parseMinuteDateTime(completedStart, false),
                    parseMinuteDateTime(completedEnd, true),
                    PageRequest.of(pageNumber(page) - 1, pageSize(size), Sort.by(Sort.Direction.DESC, "completedAt").and(Sort.by(Sort.Direction.DESC, "id")))
            ).map(ShopOrderResponse::from);
            return ApiResponse.success(PagedResponse.from(orders));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/ingest")
    public ApiResponse<ShopOrderIngestResponse> ingest(
            @RequestHeader(value = ExternalCallbackTokenService.HEADER_NAME, required = false) String callbackToken,
            @RequestBody ShopOrderIngestRequest request
    ) {
        if (externalCallbackTokenService.hasProvidedToken(callbackToken)) {
            if (!externalCallbackTokenService.isValidToken(callbackToken)) {
                return ApiResponse.error(401, "外部回调token无效");
            }
        } else {
            permissionService.requireSuperAdmin();
        }
        try {
            return ApiResponse.success(shopOrderService.ingestBatch(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/crawl-targets")
    public ApiResponse<List<AuthorizedShopOrderCrawlTarget>> crawlTargets(
            @RequestHeader(value = ExternalCallbackTokenService.HEADER_NAME, required = false) String callbackToken
    ) {
        ApiResponse<Void> tokenError = requireCallbackTokenOrSuperAdmin(callbackToken);
        if (tokenError != null) {
            return ApiResponse.error(tokenError.getCode(), tokenError.getMessage());
        }
        return ApiResponse.success(shopOrderService.authorizedCrawlTargets());
    }

    private ApiResponse<Void> requireCallbackTokenOrSuperAdmin(String callbackToken) {
        if (externalCallbackTokenService.hasProvidedToken(callbackToken)) {
            if (!externalCallbackTokenService.isValidToken(callbackToken)) {
                return ApiResponse.error(401, "外部回调token无效");
            }
            return null;
        }
        permissionService.requireSuperAdmin();
        return null;
    }

    private LocalDateTime parseMinuteDateTime(String value, boolean end) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        try {
            if (normalized.length() == 16) {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .withSecond(end ? 59 : 0)
                        .withNano(end ? 999_000_000 : 0);
            }
            if (normalized.length() == 19) {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("订单完成时间格式无效");
        }
    }

    private int pageNumber(Integer page) {
        return Math.max(1, page == null ? 1 : page);
    }

    private int pageSize(Integer size) {
        return Math.max(1, Math.min(100, size == null ? 10 : size));
    }
}
