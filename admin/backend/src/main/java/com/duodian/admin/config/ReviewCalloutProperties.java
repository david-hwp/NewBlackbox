package com.duodian.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.review-callout")
public class ReviewCalloutProperties {
    private Callback callback = new Callback();
    private Billing billing = new Billing();

    public Callback getCallback() { return callback; }
    public void setCallback(Callback callback) { this.callback = callback == null ? new Callback() : callback; }

    public Billing getBilling() { return billing; }
    public void setBilling(Billing billing) { this.billing = billing == null ? new Billing() : billing; }

    public static class Callback {
        private boolean enabled = false;
        private String token = "";
        private boolean acceptQueryToken = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token == null ? "" : token; }
        public boolean isAcceptQueryToken() { return acceptQueryToken; }
        public void setAcceptQueryToken(boolean acceptQueryToken) { this.acceptQueryToken = acceptQueryToken; }
    }

    public static class Billing {
        private String mode = "BILLED_MINUTES";
        private int rateUnitsPerMinute = 1;
        private int minimumChargeUnits = 1;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode == null ? "BILLED_MINUTES" : mode; }
        public int getRateUnitsPerMinute() { return Math.max(0, rateUnitsPerMinute); }
        public void setRateUnitsPerMinute(int rateUnitsPerMinute) { this.rateUnitsPerMinute = rateUnitsPerMinute; }
        public int getMinimumChargeUnits() { return Math.max(0, minimumChargeUnits); }
        public void setMinimumChargeUnits(int minimumChargeUnits) { this.minimumChargeUnits = minimumChargeUnits; }
    }
}
