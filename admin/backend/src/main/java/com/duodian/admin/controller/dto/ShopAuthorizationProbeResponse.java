package com.duodian.admin.controller.dto;

import java.time.LocalDateTime;

public class ShopAuthorizationProbeResponse {
    private String status;
    private String confidence;
    private LocalDateTime checkedAt;
    private String signals;

    public ShopAuthorizationProbeResponse() {
    }

    public ShopAuthorizationProbeResponse(String status, String confidence, LocalDateTime checkedAt, String signals) {
        this.status = status;
        this.confidence = confidence;
        this.checkedAt = checkedAt;
        this.signals = signals;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }

    public String getSignals() { return signals; }
    public void setSignals(String signals) { this.signals = signals; }
}
