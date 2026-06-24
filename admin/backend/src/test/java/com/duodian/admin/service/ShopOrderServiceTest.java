package com.duodian.admin.service;

import com.duodian.admin.controller.dto.ShopOrderIngestRequest;
import com.duodian.admin.controller.dto.ShopOrderIngestResponse;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.ShopOrder;
import com.duodian.admin.repository.ShopOrderRepository;
import com.duodian.admin.repository.ShopRepository;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ShopOrderService service = new ShopOrderService(shopOrderRepository, shopRepository, objectMapper);

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
        existing.setStatus("待配送");
        when(shopRepository.findByIdAndDeleted(194L, (byte) 0)).thenReturn(Optional.of(shop));
        when(shopOrderRepository.findByShopIdAndPlatformAndPlatformOrderIdAndDeleted(194L, "mtwm", "order-1", (byte) 0))
                .thenReturn(Optional.of(existing));

        ShopOrderIngestResponse response = service.ingestBatch(request(order("order-1", "已完成", LocalDateTime.of(2026, 6, 24, 13, 0))));

        assertThat(response.getInserted()).isZero();
        assertThat(response.getUpdated()).isEqualTo(1);
        verify(shopOrderRepository).save(argThat(order ->
                order.getId().equals(99L) && "已完成".equals(order.getStatus())
        ));
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

    private ShopOrderIngestRequest request(ShopOrderIngestRequest.OrderItem... orders) {
        ShopOrderIngestRequest request = new ShopOrderIngestRequest();
        request.setShopId(194L);
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
}
