package com.duodian.admin.controller.dto;

public class ShopOrderIngestResponse {
    private int received;
    private int inserted;
    private int updated;
    private int rejected;

    public ShopOrderIngestResponse() {
    }

    public ShopOrderIngestResponse(int received, int inserted, int updated, int rejected) {
        this.received = received;
        this.inserted = inserted;
        this.updated = updated;
        this.rejected = rejected;
    }

    public int getReceived() { return received; }
    public void setReceived(int received) { this.received = received; }

    public int getInserted() { return inserted; }
    public void setInserted(int inserted) { this.inserted = inserted; }

    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }

    public int getRejected() { return rejected; }
    public void setRejected(int rejected) { this.rejected = rejected; }
}
