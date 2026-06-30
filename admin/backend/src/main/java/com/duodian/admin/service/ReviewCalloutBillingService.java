package com.duodian.admin.service;

import com.duodian.admin.config.ReviewCalloutProperties;
import com.duodian.admin.entity.ShopOrderReviewCalloutResult;
import org.springframework.stereotype.Service;

@Service
public class ReviewCalloutBillingService {
    private final ReviewCalloutProperties properties;

    public ReviewCalloutBillingService(ReviewCalloutProperties properties) {
        this.properties = properties;
    }

    public BillingDecision decide(ShopOrderReviewCalloutResult result) {
        if (!isBillable(result)) {
            return BillingDecision.notBillable("NOT_CONNECTED");
        }

        int billedMinutes = result.getBilledMinutes() == null ? 0 : Math.max(0, result.getBilledMinutes());
        if (billedMinutes == 0 && result.getDurationSeconds() != null && result.getDurationSeconds() > 0) {
            billedMinutes = (int) Math.ceil(result.getDurationSeconds() / 60.0);
        }
        if (billedMinutes <= 0) {
            return BillingDecision.notBillable("ZERO_DURATION");
        }

        int minimum = properties.getBilling().getMinimumChargeUnits();
        int rate = properties.getBilling().getRateUnitsPerMinute();
        int amount;
        if ("EXTERNAL_MONEY".equalsIgnoreCase(properties.getBilling().getMode())) {
            amount = Math.max(minimum, result.getExternalMoneyCent() == null ? 0 : result.getExternalMoneyCent());
        } else {
            amount = Math.max(minimum, billedMinutes * rate);
        }
        if (amount <= 0) {
            return BillingDecision.notBillable("ZERO_AMOUNT");
        }
        return BillingDecision.billable(amount, billedMinutes, rate, minimum);
    }

    private boolean isBillable(ShopOrderReviewCalloutResult result) {
        if (Boolean.TRUE.equals(result.getConnected())) {
            return true;
        }
        String state = result.getCallState();
        if (state != null && state.trim().equals("2")) {
            return true;
        }
        String text = result.getCallStateText();
        return text != null && (text.contains("接通") || text.contains("已接"));
    }

    public record BillingDecision(
            boolean billable,
            String reason,
            int amount,
            int billedMinutes,
            int rateUnitsPerMinute,
            int minimumChargeUnits
    ) {
        static BillingDecision billable(int amount, int billedMinutes, int rateUnitsPerMinute, int minimumChargeUnits) {
            return new BillingDecision(true, "BILLABLE_CONNECTED", amount, billedMinutes, rateUnitsPerMinute, minimumChargeUnits);
        }

        static BillingDecision notBillable(String reason) {
            return new BillingDecision(false, reason, 0, 0, 0, 0);
        }
    }
}
