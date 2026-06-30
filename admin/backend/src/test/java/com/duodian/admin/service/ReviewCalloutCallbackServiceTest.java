package com.duodian.admin.service;

import com.duodian.admin.controller.dto.GookiCalloutCallbackRequest;
import com.duodian.admin.entity.ShopOrder;
import com.duodian.admin.entity.ShopOrderReviewCallout;
import com.duodian.admin.entity.ShopOrderReviewCalloutResult;
import com.duodian.admin.repository.ShopOrderRepository;
import com.duodian.admin.repository.ShopOrderReviewCalloutRepository;
import com.duodian.admin.repository.ShopOrderReviewCalloutResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ReviewCalloutCallbackServiceTest {
    private final ShopOrderReviewCalloutRepository calloutRepository = mock(ShopOrderReviewCalloutRepository.class);
    private final ShopOrderReviewCalloutResultRepository resultRepository = mock(ShopOrderReviewCalloutResultRepository.class);
    private final ShopOrderRepository shopOrderRepository = mock(ShopOrderRepository.class);
    private final ReviewCalloutBillingService billingService = mock(ReviewCalloutBillingService.class);
    private final ComputeService computeService = mock(ComputeService.class);
    private final ReviewCalloutCallbackService service = new ReviewCalloutCallbackService(
            calloutRepository,
            resultRepository,
            shopOrderRepository,
            billingService,
            computeService,
            new ObjectMapper()
    );

    @Test
    void connectedCallbackDeductsAndPersistsLedgerRelationship() {
        ShopOrderReviewCallout callout = callout();
        ShopOrder order = order();
        GookiCalloutCallbackRequest request = request();

        when(resultRepository.findByCallbackIdempotencyKeyAndDeleted(cdrKey("cdr-1"), (byte) 0)).thenReturn(Optional.empty());
        when(calloutRepository.findByIdAndDeleted(88L, (byte) 0)).thenReturn(Optional.of(callout));
        when(shopOrderRepository.findById(194L)).thenReturn(Optional.of(order));
        when(billingService.decide(any())).thenReturn(
                new ReviewCalloutBillingService.BillingDecision(true, "BILLABLE_CONNECTED", 4, 2, 2, 1)
        );
        when(computeService.consumePhoneMinutesForReviewCallout(any())).thenReturn(new ComputeService.DeductionResult(true, true, 301L));
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderReviewCalloutResult result = service.handle(request);

        assertThat(result.getBillingStatus()).isEqualTo("DEDUCTED");
        assertThat(result.getTransactionLogId()).isEqualTo(301L);
        assertThat(result.getPhoneMasked()).isEqualTo("138****8000");
        verify(computeService).consumePhoneMinutesForReviewCallout(argThat(consume ->
                consume.userId().equals(1L)
                        && consume.amount().equals(4)
                        && consume.shopOrderId().equals(194L)
                        && consume.reviewCalloutId().equals(88L)
                        && "cdr-1".equals(consume.externalCdrId())
        ));
    }

    @Test
    void duplicateCallbackReturnsExistingResultWithoutDeducting() {
        ShopOrderReviewCalloutResult existing = new ShopOrderReviewCalloutResult();
        existing.setBillingStatus("DEDUCTED");
        when(resultRepository.findByCallbackIdempotencyKeyAndDeleted(cdrKey("cdr-1"), (byte) 0)).thenReturn(Optional.of(existing));

        ShopOrderReviewCalloutResult result = service.handle(request());

        assertThat(result).isSameAs(existing);
        verify(computeService, never()).consumePhoneMinutesForReviewCallout(any());
        verify(resultRepository, never()).save(any());
    }

    @Test
    void unmatchedCallbackIsStoredWithoutBilling() {
        GookiCalloutCallbackRequest request = request();
        when(resultRepository.findByCallbackIdempotencyKeyAndDeleted(cdrKey("cdr-1"), (byte) 0)).thenReturn(Optional.empty());
        when(calloutRepository.findByIdAndDeleted(88L, (byte) 0)).thenReturn(Optional.empty());
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderReviewCalloutResult result = service.handle(request);

        assertThat(result.getBillingStatus()).isEqualTo("UNMATCHED");
        assertThat(result.getLastError()).contains("未匹配");
        verify(computeService, never()).consumePhoneMinutesForReviewCallout(any());
    }

    @Test
    void paramsAndRawSummaryAreSanitizedBeforePersisting() {
        GookiCalloutCallbackRequest request = request();
        request.setPhone("13900139000");
        request.setParams(Map.of(
                "本地外呼记录ID", 88,
                "系统订单ID", 194,
                "token", "secret-token",
                "手机号", "13900139000",
                "备注", "客户电话13900139000"
        ));

        when(resultRepository.findByCallbackIdempotencyKeyAndDeleted(cdrKey("cdr-1"), (byte) 0)).thenReturn(Optional.empty());
        when(calloutRepository.findByIdAndDeleted(88L, (byte) 0)).thenReturn(Optional.empty());
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderReviewCalloutResult result = service.handle(request);

        assertThat(result.getParamsSummary()).doesNotContain("secret-token", "13900139000");
        assertThat(result.getParamsSummary()).contains("[REDACTED]", "139****9000");
        assertThat(result.getRawSummary()).doesNotContain("secret-token", "13900139000");
        assertThat(result.getRawSummary()).contains("[REDACTED]", "139****9000");
    }

    @Test
    void insufficientBalanceIsPersistedWithoutNegativeDeduction() {
        when(resultRepository.findByCallbackIdempotencyKeyAndDeleted(cdrKey("cdr-1"), (byte) 0)).thenReturn(Optional.empty());
        when(calloutRepository.findByIdAndDeleted(88L, (byte) 0)).thenReturn(Optional.of(callout()));
        when(shopOrderRepository.findById(194L)).thenReturn(Optional.of(order()));
        when(billingService.decide(any())).thenReturn(
                new ReviewCalloutBillingService.BillingDecision(true, "BILLABLE_CONNECTED", 4, 2, 2, 1)
        );
        when(computeService.consumePhoneMinutesForReviewCallout(any())).thenReturn(ComputeService.DeductionResult.insufficient());
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderReviewCalloutResult result = service.handle(request());

        assertThat(result.getBillingStatus()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(result.getTransactionLogId()).isNull();
    }

    @Test
    void externalIdsAreBoundedBeforePersisting() {
        String longCdr = "cdr-" + "x".repeat(200);
        String longTask = "task-" + "y".repeat(200);
        GookiCalloutCallbackRequest request = request();
        request.setCdrId(longCdr);
        request.setTaskId(longTask);

        when(resultRepository.findByCallbackIdempotencyKeyAndDeleted(cdrKey(longCdr), (byte) 0)).thenReturn(Optional.empty());
        when(calloutRepository.findByIdAndDeleted(88L, (byte) 0)).thenReturn(Optional.empty());
        when(resultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderReviewCalloutResult result = service.handle(request);

        assertThat(result.getExternalCdrId()).hasSize(128);
        assertThat(result.getExternalTaskId()).hasSize(128);
        assertThat(result.getCallbackIdempotencyKey()).isEqualTo(cdrKey(longCdr));
    }

    private GookiCalloutCallbackRequest request() {
        GookiCalloutCallbackRequest request = new GookiCalloutCallbackRequest();
        request.setCdrId("cdr-1");
        request.setTaskId("task-1");
        request.setPhone("13800138000");
        request.setCallState(2);
        request.setCallTime(1782801600);
        request.setDuration(61);
        request.setMinute(2);
        request.setMoney(12);
        request.setParams(Map.of("本地外呼记录ID", 88, "系统订单ID", 194));
        return request;
    }

    private ShopOrderReviewCallout callout() {
        ShopOrderReviewCallout callout = new ShopOrderReviewCallout();
        callout.setId(88L);
        callout.setShopOrderId(194L);
        callout.setShopId(76L);
        callout.setUserId(1L);
        callout.setChannelId(2L);
        callout.setPlatform("mtwm");
        callout.setPlatformOrderId("order-1");
        callout.setShopName("极点披萨");
        callout.setExternalTaskId("task-1");
        return callout;
    }

    private ShopOrder order() {
        ShopOrder order = new ShopOrder();
        order.setId(194L);
        order.setShopId(76L);
        order.setUserId(1L);
        order.setChannelId(2L);
        order.setPlatform("mtwm");
        order.setPlatformOrderId("order-1");
        order.setShopName("极点披萨");
        order.setCompletedAt(LocalDateTime.of(2026, 6, 30, 11, 0));
        return order;
    }

    private String cdrKey(String cdrId) {
        return "cdr:" + sha256(cdrId);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
