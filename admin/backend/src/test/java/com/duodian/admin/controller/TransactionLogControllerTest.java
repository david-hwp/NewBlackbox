package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.TransactionLogResponse;
import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.repository.TransactionLogRepository;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.TransactionLogService;
import com.duodian.admin.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionLogControllerTest {
    private final TransactionLogService logService = mock(TransactionLogService.class);
    private final TransactionLogRepository logRepository = mock(TransactionLogRepository.class);
    private final UserService userService = mock(UserService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final TransactionLogController controller = new TransactionLogController(
            logService,
            logRepository,
            userService,
            permissionService
    );

    @Test
    void listPhoneConsumeLogsIncludesReviewCalloutFields() {
        LocalDateTime calledAt = LocalDateTime.of(2026, 6, 30, 9, 15);
        TransactionLog log = new TransactionLog();
        log.setId(501L);
        log.setType("PHONE_CONSUME");
        log.setAmount(2);
        log.setShopName("极点披萨");
        log.setShopOrderId(1001L);
        log.setReviewCalloutId(88L);
        log.setExternalTaskId("task-1");
        log.setExternalCdrId("cdr-1");
        log.setCalledAt(calledAt);
        log.setBillingRate(1);

        when(permissionService.filterChannelForQuery(null)).thenReturn(null);
        when(logRepository.searchLogs(
                eq((byte) 0),
                nullable(Long.class),
                nullable(Long.class),
                eq("PHONE_CONSUME"),
                nullable(String.class),
                nullable(String.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1));

        ApiResponse<?> response = controller.list(null, "PHONE_CONSUME", null, null, null, 1, 10);

        assertThat(response.getCode()).isEqualTo(200);
        PagedResponse<?> page = (PagedResponse<?>) response.getData();
        assertThat(page.getTotal()).isEqualTo(1L);
        TransactionLogResponse item = (TransactionLogResponse) page.getList().get(0);
        assertThat(item.getShopOrderId()).isEqualTo(1001L);
        assertThat(item.getReviewCalloutId()).isEqualTo(88L);
        assertThat(item.getExternalTaskId()).isEqualTo("task-1");
        assertThat(item.getExternalCdrId()).isEqualTo("cdr-1");
        assertThat(item.getCalledAt()).isEqualTo(calledAt);
        assertThat(item.getBillingRate()).isEqualTo(1);
        verify(permissionService).requireAdminRole();
    }
}
