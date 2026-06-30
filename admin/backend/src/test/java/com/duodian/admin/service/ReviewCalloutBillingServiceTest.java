package com.duodian.admin.service;

import com.duodian.admin.config.ReviewCalloutProperties;
import com.duodian.admin.entity.ShopOrderReviewCalloutResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewCalloutBillingServiceTest {
    private final ReviewCalloutProperties properties = new ReviewCalloutProperties();
    private final ReviewCalloutBillingService service = new ReviewCalloutBillingService(properties);

    @Test
    void connectedCallUsesBilledMinutesAndRate() {
        properties.getBilling().setRateUnitsPerMinute(2);
        properties.getBilling().setMinimumChargeUnits(1);
        ShopOrderReviewCalloutResult result = new ShopOrderReviewCalloutResult();
        result.setCallState("2");
        result.setBilledMinutes(3);

        ReviewCalloutBillingService.BillingDecision decision = service.decide(result);

        assertThat(decision.billable()).isTrue();
        assertThat(decision.amount()).isEqualTo(6);
        assertThat(decision.billedMinutes()).isEqualTo(3);
    }

    @Test
    void connectedCallFallsBackToDurationCeil() {
        properties.getBilling().setRateUnitsPerMinute(2);
        ShopOrderReviewCalloutResult result = new ShopOrderReviewCalloutResult();
        result.setConnected(true);
        result.setDurationSeconds(61);

        ReviewCalloutBillingService.BillingDecision decision = service.decide(result);

        assertThat(decision.billable()).isTrue();
        assertThat(decision.billedMinutes()).isEqualTo(2);
        assertThat(decision.amount()).isEqualTo(4);
    }

    @Test
    void notConnectedIsNotBillable() {
        ShopOrderReviewCalloutResult result = new ShopOrderReviewCalloutResult();
        result.setCallState("1");

        ReviewCalloutBillingService.BillingDecision decision = service.decide(result);

        assertThat(decision.billable()).isFalse();
        assertThat(decision.reason()).isEqualTo("NOT_CONNECTED");
    }
}
