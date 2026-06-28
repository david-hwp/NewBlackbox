package com.duodian.admin.service;

import com.duodian.admin.controller.dto.AuthorizedShopOrderCrawlTarget;
import com.duodian.admin.controller.dto.ShopOrderIngestRequest;
import com.duodian.admin.controller.dto.ShopOrderIngestResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.ShopOrder;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ShopOrderRepository;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ShopOrderService {
    private static final byte ACTIVE = 0;
    private static final Set<String> SENSITIVE_PAYLOAD_KEYS = Set.of(
            "cookie",
            "cookies",
            "token",
            "authorization",
            "auth",
            "storage",
            "localStorage",
            "sessionStorage",
            "profile",
            "profileDir",
            "profilePath",
            "debugPort",
            "browserStorage",
            "shopAuthorizationSignals"
    );
    private static final Set<String> SUPPORTED_ORDER_CRAWL_PLATFORMS = Set.of("mtwm", "jdms", "tbwm");

    private final ShopOrderRepository shopOrderRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ShopOrderService(
            ShopOrderRepository shopOrderRepository,
            ShopRepository shopRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.shopOrderRepository = shopOrderRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public Page<ShopOrder> search(
            Long shopId,
            String platform,
            String status,
            String shopName,
            String customerKeyword,
            String orderKeyword,
            LocalDateTime completedStart,
            LocalDateTime completedEnd,
            Pageable pageable
    ) {
        return shopOrderRepository.searchOrders(
                ACTIVE,
                shopId,
                normalize(platform),
                normalize(status),
                normalize(shopName),
                normalize(customerKeyword),
                normalize(orderKeyword),
                completedStart,
                completedEnd,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public List<AuthorizedShopOrderCrawlTarget> authorizedCrawlTargets() {
        return shopRepository.findAuthorizedOrderCrawlTargets(ACTIVE, SUPPORTED_ORDER_CRAWL_PLATFORMS).stream()
                .map(shop -> AuthorizedShopOrderCrawlTarget.from(
                        shop,
                        userRepository.findByIdAndDeleted(shop.getUserId(), ACTIVE).orElse(null)
                ))
                .filter(target -> normalize(target.getUserPhone()) != null)
                .toList();
    }

    @Transactional
    public ShopOrderIngestResponse ingestBatch(ShopOrderIngestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getShopId() == null) {
            throw new IllegalArgumentException("缺少系统店铺ID");
        }
        Shop shop = shopRepository.findByIdAndDeleted(request.getShopId(), ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("店铺不存在"));
        int received = request.getOrders() == null ? 0 : request.getOrders().size();
        if (received == 0) {
            return new ShopOrderIngestResponse(0, 0, 0, 0);
        }

        int inserted = 0;
        int updated = 0;
        int rejected = 0;
        for (ShopOrderIngestRequest.OrderItem item : request.getOrders()) {
            String platformOrderId = normalize(item == null ? null : item.getPlatformOrderId());
            if (platformOrderId == null) {
                rejected++;
                continue;
            }
            String platform = normalize(shop.getPlatform());
            if (platform == null) {
                platform = "unknown";
            }
            Optional<ShopOrder> existing = shopOrderRepository.findByShopIdAndPlatformAndPlatformOrderIdAndDeleted(
                    shop.getId(),
                    platform,
                    platformOrderId,
                    ACTIVE
            );
            ShopOrder order = existing.orElseGet(ShopOrder::new);
            applySnapshots(order, shop, platform);
            applyRequest(order, request, item, platformOrderId);
            shopOrderRepository.save(order);
            if (existing.isPresent()) {
                updated++;
            } else {
                inserted++;
            }
        }
        return new ShopOrderIngestResponse(received, inserted, updated, rejected);
    }

    private void applySnapshots(ShopOrder order, Shop shop, String platform) {
        order.setShopId(shop.getId());
        order.setUserId(shop.getUserId());
        order.setChannelId(shop.getChannelId());
        order.setPlatform(platform);
        order.setPlatformName(normalize(shop.getPlatformName()));
        order.setPlatformShopId(normalize(shop.getShopId()));
        order.setShopName(normalize(shop.getShopName()));
    }

    private void applyRequest(
            ShopOrder order,
            ShopOrderIngestRequest request,
            ShopOrderIngestRequest.OrderItem item,
            String platformOrderId
    ) {
        LocalDateTime now = LocalDateTime.now();
        order.setPlatformOrderId(platformOrderId);
        setIfPresent(order::setPlatformOrderNo, firstNonBlank(item.getPlatformOrderNo(), item.getOrderSequence()));
        setIfPresent(order::setOrderSequence, firstNonBlank(item.getOrderSequence(), item.getPlatformOrderNo()));
        setIfPresent(order::setSource, firstNonBlank(normalize(request.getSource()), "fetch_meituan_orders"));
        setIfPresent(order::setOrderTimeText, normalize(item.getOrderTimeText()));
        setIfPresent(order::setOrderedAt, item.getOrderedAt());
        setIfPresent(order::setExpectedDeliveryAt, item.getExpectedDeliveryAt());
        setIfPresent(order::setCompletedAt, item.getCompletedAt());
        setIfPresent(order::setCancelledAt, item.getCancelledAt());
        setIfPresent(order::setRefundedAt, item.getRefundedAt());
        order.setFetchedAt(item.getFetchedAt() == null ? now : item.getFetchedAt());
        order.setLastSeenAt(now);
        setIfPresent(order::setStatus, normalize(item.getStatus()));
        setIfPresent(order::setStatusText, firstNonBlank(item.getStatusText(), item.getStatus()));
        setIfPresent(order::setOrderType, normalize(item.getOrderType()));
        setIfPresent(order::setTagsJson, toJson(item.getTags()));
        setIfPresent(order::setEstimatedIncome, item.getEstimatedIncome());
        setIfPresent(order::setCustomerPaidAmount, item.getCustomerPaidAmount());
        setIfPresent(order::setMerchantIncome, item.getMerchantIncome());
        setIfPresent(order::setOriginalAmount, item.getOriginalAmount());
        setIfPresent(order::setDiscountAmount, item.getDiscountAmount());
        setIfPresent(order::setDeliveryFee, item.getDeliveryFee());
        setIfPresent(order::setPackageFee, item.getPackageFee());
        setIfPresent(order::setRefundAmount, item.getRefundAmount());
        setIfPresent(order::setCurrency, firstNonBlank(item.getCurrency(), order.getCurrency(), "CNY"));
        setIfPresent(order::setCustomerName, normalize(item.getCustomerName()));
        setIfPresent(order::setCustomerPhoneTail, normalize(item.getCustomerPhoneTail()));
        setIfPresent(order::setPrivacyPhone, normalize(item.getPrivacyPhone()));
        setIfPresent(order::setBackupPhone, normalize(item.getBackupPhone()));
        setIfPresent(order::setAddress, normalize(item.getAddress()));
        setIfPresent(order::setRecipientAddress, firstNonBlank(item.getRecipientAddress(), item.getAddress()));
        setIfPresent(order::setDeliveryType, normalize(item.getDeliveryType()));
        setIfPresent(order::setRiderName, normalize(item.getRiderName()));
        setIfPresent(order::setRiderPhone, normalize(item.getRiderPhone()));
        setIfPresent(order::setRemark, normalize(item.getRemark()));
        setIfPresent(order::setItemSummary, normalize(item.getItemSummary()));
        setIfPresent(order::setItemCount, item.getItemCount());
        setIfPresent(order::setItemsJson, toJson(item.getItems()));
        setIfPresent(order::setRawText, normalize(item.getRawText()));
        setIfPresent(order::setRawPayload, toJson(sanitizePayload(item.getRawPayload())));
        setIfPresent(order::setIngestBatchId, normalize(request.getIngestBatchId()));
        order.setDeleted(ACTIVE);
    }

    private JsonNode sanitizePayload(JsonNode rawPayload) {
        if (rawPayload == null || rawPayload.isNull()) {
            return null;
        }
        JsonNode copy = rawPayload.deepCopy();
        sanitizeNode(copy);
        return copy;
    }

    private void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    fields.remove();
                } else {
                    sanitizeNode(field.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                sanitizeNode(child);
            }
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        for (String sensitive : SENSITIVE_PAYLOAD_KEYS) {
            if (sensitive.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private void setIfPresent(java.util.function.Consumer<String> setter, String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            setter.accept(normalized);
        }
    }

    private void setIfPresent(java.util.function.Consumer<LocalDateTime> setter, LocalDateTime value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setIfPresent(java.util.function.Consumer<BigDecimal> setter, BigDecimal value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void setIfPresent(java.util.function.Consumer<Integer> setter, Integer value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
