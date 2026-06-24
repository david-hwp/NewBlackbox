package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.ShopOrderIngestRequest;
import com.duodian.admin.controller.dto.ShopOrderIngestResponse;
import com.duodian.admin.controller.dto.ShopOrderResponse;
import com.duodian.admin.entity.ShopOrder;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.ShopOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ShopOrderControllerTest {
    private final ShopOrderService shopOrderService = mock(ShopOrderService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final ShopOrderController controller = new ShopOrderController(shopOrderService, permissionService);

    @Test
    void listRequiresSuperAdmin() {
        doThrow(new RuntimeException("无权限")).when(permissionService).requireSuperAdmin();

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null, null, 1, 10))
                .hasMessage("无权限");
        verify(shopOrderService, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listPassesMinutePrecisionCompletedRangeToService() {
        ShopOrder order = new ShopOrder();
        order.setId(1L);
        order.setShopId(194L);
        order.setPlatform("mtwm");
        order.setPlatformOrderId("order-1");
        order.setCompletedAt(LocalDateTime.of(2026, 6, 24, 12, 34, 30));
        when(shopOrderService.search(
                eq(194L),
                eq("mtwm"),
                eq("已完成"),
                eq("极点"),
                eq("张"),
                eq("order"),
                eq(LocalDateTime.of(2026, 6, 24, 0, 0, 0, 0)),
                eq(LocalDateTime.of(2026, 6, 24, 12, 34, 59, 999_000_000)),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        ApiResponse<PagedResponse<ShopOrderResponse>> response = controller.list(
                194L,
                "mtwm",
                "已完成",
                "极点",
                "张",
                "order",
                "2026-06-24 00:00",
                "2026-06-24 12:34",
                1,
                20
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal()).isEqualTo(1);
        verify(permissionService).requireSuperAdmin();
    }

    @Test
    void ingestRequiresSuperAdminAndDelegatesToService() {
        ShopOrderIngestRequest request = new ShopOrderIngestRequest();
        request.setShopId(194L);
        when(shopOrderService.ingestBatch(request)).thenReturn(new ShopOrderIngestResponse(1, 1, 0, 0));

        ApiResponse<ShopOrderIngestResponse> response = controller.ingest(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getInserted()).isEqualTo(1);
        verify(permissionService).requireSuperAdmin();
        verify(shopOrderService).ingestBatch(request);
    }

    @Test
    void ingestReturnsBadRequestForInvalidShopOrderPayload() {
        ShopOrderIngestRequest request = new ShopOrderIngestRequest();
        when(shopOrderService.ingestBatch(request)).thenThrow(new IllegalArgumentException("缺少系统店铺ID"));

        ApiResponse<ShopOrderIngestResponse> response = controller.ingest(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("缺少系统店铺ID");
        verify(permissionService).requireSuperAdmin();
    }
}
