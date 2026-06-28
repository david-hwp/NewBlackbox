package com.duodian.admin.service;

import com.duodian.admin.controller.dto.ShopOrderIngestRequest;
import com.duodian.admin.controller.dto.ShopOrderIngestResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.ShopOrder;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.ShopOrderRepository;
import com.duodian.admin.repository.ShopRepository;
import com.duodian.admin.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ShopOrderServiceTest {
    private final ShopOrderRepository shopOrderRepository = mock(ShopOrderRepository.class);
    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ShopOrderService service = new ShopOrderService(shopOrderRepository, shopRepository, userRepository, objectMapper);

    @Test
    void ingestBatchCreatesDetailedShopOrderAndDerivesShopSnapshots() {
        Shop shop = shop();
        when(shopRepository.findByIdAndDeleted(194L, (byte) 0)).thenReturn(Optional.of(shop));
        when(shopOrderRepository.findByShopIdAndPlatformAndPlatformOrderIdAndDeleted(194L, "mtwm", "1802176573697106215", (byte) 0))
                .thenReturn(Optional.empty());
        when(shopOrderRepository.save(any(ShopOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderIngestRequest request = request(order("1802176573697106215", "用户已收餐", LocalDateTime.of(2026, 6, 24, 12, 30)));
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("orderId", "1802176573697106215");
        raw.put("cookie", "secret");
        raw.put("token", "secret");
        raw.put("address", "深圳市南山区");
        request.getOrders().get(0).setRawPayload(raw);

        ShopOrderIngestResponse response = service.ingestBatch(request);

        assertThat(response.getReceived()).isEqualTo(1);
        assertThat(response.getInserted()).isEqualTo(1);
        assertThat(response.getUpdated()).isZero();
        verify(shopOrderRepository).save(argThat(order ->
                order.getShopId().equals(194L)
                        && order.getUserId().equals(10L)
                        && order.getChannelId().equals(2L)
                        && "mtwm".equals(order.getPlatform())
                        && "极点披萨".equals(order.getShopName())
                        && "15397100".equals(order.getPlatformShopId())
                        && "1802176573697106215".equals(order.getPlatformOrderId())
                        && "用户已收餐".equals(order.getStatus())
                        && new BigDecimal("33.50").compareTo(order.getEstimatedIncome()) == 0
                        && order.getRawPayload().contains("orderId")
                        && !order.getRawPayload().contains("cookie")
                        && !order.getRawPayload().contains("token")
        ));
    }

    @Test
    void ingestBatchUpdatesExistingOrderInsteadOfDuplicating() {
        Shop shop = shop();
        ShopOrder existing = new ShopOrder();
        existing.setId(99L);
        existing.setShopId(194L);
        existing.setPlatform("mtwm");
        existing.setPlatformOrderId("order-1");
        existing.setStatus("骑手已取餐");
        existing.setPrivacyPhone("13800000000 转 1234");
        existing.setAddress("深圳市南山区");
        existing.setEstimatedIncome(new BigDecimal("33.50"));
        when(shopRepository.findByIdAndDeleted(194L, (byte) 0)).thenReturn(Optional.of(shop));
        when(shopOrderRepository.findByShopIdAndPlatformAndPlatformOrderIdAndDeleted(194L, "mtwm", "order-1", (byte) 0))
                .thenReturn(Optional.of(existing));

        ShopOrderIngestRequest.OrderItem updated = new ShopOrderIngestRequest.OrderItem();
        updated.setPlatformOrderId("order-1");
        updated.setStatus("用户已收餐");
        updated.setCompletedAt(LocalDateTime.of(2026, 6, 24, 13, 0));
        updated.setFetchedAt(LocalDateTime.of(2026, 6, 24, 13, 2));
        ShopOrderIngestResponse response = service.ingestBatch(request(updated));

        assertThat(response.getInserted()).isZero();
        assertThat(response.getUpdated()).isEqualTo(1);
        verify(shopOrderRepository).save(argThat(order ->
                order.getId().equals(99L)
                        && "用户已收餐".equals(order.getStatus())
                        && LocalDateTime.of(2026, 6, 24, 13, 0).equals(order.getCompletedAt())
                        && "13800000000 转 1234".equals(order.getPrivacyPhone())
                        && "深圳市南山区".equals(order.getAddress())
                        && new BigDecimal("33.50").compareTo(order.getEstimatedIncome()) == 0
        ));
    }

    @Test
    void authorizedCrawlTargetsReturnOnlyAuthorizedSupportedShopsWithSystemProfileIds() {
        Shop authorized = shop();
        Shop jdAuthorized = jdShop();
        Shop tbwmAuthorized = tbwmShop();
        User owner = new User();
        owner.setId(10L);
        owner.setPhone("15200837196");
        when(shopRepository.findAuthorizedOrderCrawlTargets((byte) 0, java.util.Set.of("mtwm", "jdms", "tbwm")))
                .thenReturn(List.of(authorized, jdAuthorized, tbwmAuthorized));
        when(userRepository.findByIdAndDeleted(10L, (byte) 0)).thenReturn(Optional.of(owner));

        var targets = service.authorizedCrawlTargets();

        assertThat(targets).hasSize(3);
        assertThat(targets.get(0).getSystemShopId()).isEqualTo(194L);
        assertThat(targets.get(0).getUserPhone()).isEqualTo("15200837196");
        assertThat(targets.get(0).getControlShopId()).isEqualTo("15397100");
        assertThat(targets.get(0).getPlatform()).isEqualTo("mtwm");
        assertThat(targets.get(1).getSystemShopId()).isEqualTo(76L);
        assertThat(targets.get(1).getControlShopId()).isEqualTo("16081572");
        assertThat(targets.get(1).getPlatform()).isEqualTo("jdms");
        assertThat(targets.get(2).getSystemShopId()).isEqualTo(120L);
        assertThat(targets.get(2).getControlShopId()).isEqualTo("1184657317");
        assertThat(targets.get(2).getPlatform()).isEqualTo("tbwm");
    }

    @Test
    void authorizedCrawlTargetsUseSystemProfileIdForPendingPlatformShopIds() {
        Shop shop = shop();
        shop.setShopId("NEW-1928cee6efbb9dd2f58ad9d942e4f030");
        User owner = new User();
        owner.setId(10L);
        owner.setPhone("15200837196");
        when(shopRepository.findAuthorizedOrderCrawlTargets((byte) 0, java.util.Set.of("mtwm", "jdms", "tbwm")))
                .thenReturn(List.of(shop));
        when(userRepository.findByIdAndDeleted(10L, (byte) 0)).thenReturn(Optional.of(owner));

        var targets = service.authorizedCrawlTargets();

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getControlShopId()).isEqualTo("system-194");
    }

    @Test
    void ingestBatchRejectsMissingOrderIdsAndReportsRejectedCount() {
        Shop shop = shop();
        when(shopRepository.findByIdAndDeleted(194L, (byte) 0)).thenReturn(Optional.of(shop));
        ShopOrderIngestRequest.OrderItem invalid = order(" ", "已完成", LocalDateTime.of(2026, 6, 24, 13, 0));

        ShopOrderIngestResponse response = service.ingestBatch(request(invalid));

        assertThat(response.getReceived()).isEqualTo(1);
        assertThat(response.getRejected()).isEqualTo(1);
        verify(shopOrderRepository, never()).save(any());
    }

    @Test
    void ingestBatchRejectsUnknownShop() {
        when(shopRepository.findByIdAndDeleted(194L, (byte) 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingestBatch(request(order("order-1", "已完成", LocalDateTime.now()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("店铺不存在");
    }

    @Test
    void ingestBatchUpsertsJdOrderAndPreservesExistingDetails() {
        Shop jdShop = jdShop();
        when(shopRepository.findByIdAndDeleted(76L, (byte) 0)).thenReturn(Optional.of(jdShop));
        when(shopOrderRepository.findByShopIdAndPlatformAndPlatformOrderIdAndDeleted(76L, "jdms", "3542401007390110", (byte) 0))
                .thenReturn(Optional.empty());
        when(shopOrderRepository.save(any(ShopOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderIngestRequest.OrderItem first = jdOrder("3542401007390110", "骑手已取餐", LocalDateTime.of(2026, 6, 27, 20, 49));
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("orderId", "3542401007390110");
        raw.put("stationNo", "16081572");
        raw.put("token", "secret");
        first.setRawPayload(raw);

        ShopOrderIngestResponse inserted = service.ingestBatch(requestForShop(76L, first));

        assertThat(inserted.getInserted()).isEqualTo(1);
        verify(shopOrderRepository).save(argThat(order ->
                "jdms".equals(order.getPlatform())
                        && "3542401007390110".equals(order.getPlatformOrderId())
                        && "骑手已取餐".equals(order.getStatus())
                        && "罗家臭豆腐（万家丽店）".equals(order.getShopName())
                        && "16081572".equals(order.getPlatformShopId())
                        && order.getRawPayload().contains("stationNo")
                        && !order.getRawPayload().contains("token")
        ));

        ShopOrder existing = new ShopOrder();
        existing.setId(77L);
        existing.setShopId(76L);
        existing.setPlatform("jdms");
        existing.setPlatformOrderId("3542401007390110");
        existing.setCustomerName("李**");
        existing.setAddress("长沙市");
        existing.setEstimatedIncome(new BigDecimal("15.95"));
        reset(shopOrderRepository);
        when(shopOrderRepository.findByShopIdAndPlatformAndPlatformOrderIdAndDeleted(76L, "jdms", "3542401007390110", (byte) 0))
                .thenReturn(Optional.of(existing));

        ShopOrderIngestRequest.OrderItem second = new ShopOrderIngestRequest.OrderItem();
        second.setPlatformOrderId("3542401007390110");
        second.setStatus("用户已收餐");
        second.setCompletedAt(LocalDateTime.of(2026, 6, 27, 21, 4));
        ShopOrderIngestResponse updated = service.ingestBatch(requestForShop(76L, second));

        assertThat(updated.getInserted()).isZero();
        assertThat(updated.getUpdated()).isEqualTo(1);
        verify(shopOrderRepository).save(argThat(order ->
                order.getId().equals(77L)
                        && "用户已收餐".equals(order.getStatus())
                        && LocalDateTime.of(2026, 6, 27, 21, 4).equals(order.getCompletedAt())
                        && "李**".equals(order.getCustomerName())
                        && "长沙市".equals(order.getAddress())
                        && new BigDecimal("15.95").compareTo(order.getEstimatedIncome()) == 0
        ));
    }

    private ShopOrderIngestRequest request(ShopOrderIngestRequest.OrderItem... orders) {
        return requestForShop(194L, orders);
    }

    private ShopOrderIngestRequest requestForShop(Long shopId, ShopOrderIngestRequest.OrderItem... orders) {
        ShopOrderIngestRequest request = new ShopOrderIngestRequest();
        request.setShopId(shopId);
        request.setSource("fetch_meituan_orders");
        request.setIngestBatchId("batch-1");
        request.setOrders(List.of(orders));
        return request;
    }

    private ShopOrderIngestRequest.OrderItem order(String orderId, String status, LocalDateTime completedAt) {
        ShopOrderIngestRequest.OrderItem order = new ShopOrderIngestRequest.OrderItem();
        order.setPlatformOrderId(orderId);
        order.setPlatformOrderNo("9");
        order.setOrderSequence("9");
        order.setOrderTimeText("06-24 12:30下单");
        order.setOrderedAt(LocalDateTime.of(2026, 6, 24, 12, 30));
        order.setExpectedDeliveryAt(LocalDateTime.of(2026, 6, 24, 13, 0));
        order.setCompletedAt(completedAt);
        order.setFetchedAt(LocalDateTime.of(2026, 6, 24, 13, 2));
        order.setStatus(status);
        order.setEstimatedIncome(new BigDecimal("33.50"));
        order.setCustomerName("张三");
        order.setCustomerPhoneTail("1234");
        order.setPrivacyPhone("13800000000 转 1234");
        order.setBackupPhone("13900000000 转 5678");
        order.setAddress("深圳市南山区");
        order.setRawText("订单编号：" + orderId);
        return order;
    }

    private ShopOrderIngestRequest.OrderItem jdOrder(String orderId, String status, LocalDateTime expectedAt) {
        ShopOrderIngestRequest.OrderItem order = new ShopOrderIngestRequest.OrderItem();
        order.setPlatformOrderId(orderId);
        order.setPlatformOrderNo("8");
        order.setOrderSequence("8");
        order.setOrderTimeText("06-27 20:49前送达");
        order.setExpectedDeliveryAt(expectedAt);
        order.setOrderedAt(LocalDateTime.of(2026, 6, 27, 19, 58));
        order.setFetchedAt(LocalDateTime.of(2026, 6, 27, 20, 50));
        order.setStatus(status);
        order.setEstimatedIncome(new BigDecimal("15.95"));
        order.setMerchantIncome(new BigDecimal("15.95"));
        order.setCustomerName("李**");
        order.setCustomerPhoneTail("2650");
        order.setAddress("长沙市");
        order.setRecipientAddress("长沙市");
        order.setDeliveryType("达达专送");
        order.setItemSummary("2种商品，共2件");
        order.setItemCount(2);
        order.setRawText("订单编号：" + orderId);
        return order;
    }

    private Shop shop() {
        Shop shop = new Shop();
        shop.setId(194L);
        shop.setUserId(10L);
        shop.setChannelId(2L);
        shop.setShopName("极点披萨");
        shop.setShopId("15397100");
        shop.setPlatform("mtwm");
        shop.setPlatformName("美团外卖");
        return shop;
    }

    private Shop jdShop() {
        Shop shop = new Shop();
        shop.setId(76L);
        shop.setUserId(10L);
        shop.setChannelId(2L);
        shop.setShopName("罗家臭豆腐（万家丽店）");
        shop.setShopId("16081572");
        shop.setPlatform("jdms");
        shop.setPlatformName("京东秒送");
        return shop;
    }

    private Shop tbwmShop() {
        Shop shop = new Shop();
        shop.setId(120L);
        shop.setUserId(10L);
        shop.setChannelId(2L);
        shop.setShopName("罗家臭豆腐（饿了么店）");
        shop.setShopId("1184657317");
        shop.setPlatform("tbwm");
        shop.setPlatformName("淘宝闪购饿了么");
        return shop;
    }
}
