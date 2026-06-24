package com.duodian.admin.controller.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ShopOrderIngestRequest {
    @JsonAlias({"system_shop_id", "systemShopId"})
    private Long shopId;
    private String source;
    @JsonAlias({"ingest_batch_id", "batchId"})
    private String ingestBatchId;
    private List<OrderItem> orders;

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getIngestBatchId() { return ingestBatchId; }
    public void setIngestBatchId(String ingestBatchId) { this.ingestBatchId = ingestBatchId; }

    public List<OrderItem> getOrders() { return orders; }
    public void setOrders(List<OrderItem> orders) { this.orders = orders; }

    public static class OrderItem {
        @JsonAlias({"order_id", "platform_order_id", "wm_order_id_view"})
        private String platformOrderId;
        @JsonAlias({"order_no", "platform_order_no"})
        private String platformOrderNo;
        @JsonAlias({"order_sequence", "sequence"})
        private String orderSequence;
        @JsonAlias({"order_time", "order_time_text"})
        private String orderTimeText;
        @JsonAlias({"ordered_at", "orderedAt"})
        private LocalDateTime orderedAt;
        @JsonAlias({"expected_delivery_at", "expectedDeliveryAt"})
        private LocalDateTime expectedDeliveryAt;
        @JsonAlias({"completed_at", "completedAt"})
        private LocalDateTime completedAt;
        @JsonAlias({"cancelled_at", "cancelledAt"})
        private LocalDateTime cancelledAt;
        @JsonAlias({"refunded_at", "refundedAt"})
        private LocalDateTime refundedAt;
        @JsonAlias({"fetched_at", "fetchedAt"})
        private LocalDateTime fetchedAt;
        private String status;
        @JsonAlias({"status_text"})
        private String statusText;
        @JsonAlias({"order_type"})
        private String orderType;
        @JsonAlias({"tags_json"})
        private JsonNode tags;
        @JsonAlias({"estimated_income"})
        private BigDecimal estimatedIncome;
        @JsonAlias({"customer_paid_amount"})
        private BigDecimal customerPaidAmount;
        @JsonAlias({"merchant_income"})
        private BigDecimal merchantIncome;
        @JsonAlias({"original_amount"})
        private BigDecimal originalAmount;
        @JsonAlias({"discount_amount"})
        private BigDecimal discountAmount;
        @JsonAlias({"delivery_fee"})
        private BigDecimal deliveryFee;
        @JsonAlias({"package_fee"})
        private BigDecimal packageFee;
        @JsonAlias({"refund_amount"})
        private BigDecimal refundAmount;
        private String currency;
        @JsonAlias({"customer_name"})
        private String customerName;
        @JsonAlias({"customer_phone_tail"})
        private String customerPhoneTail;
        @JsonAlias({"privacy_phone"})
        private String privacyPhone;
        @JsonAlias({"backup_phone"})
        private String backupPhone;
        private String address;
        @JsonAlias({"recipient_address"})
        private String recipientAddress;
        @JsonAlias({"delivery_type"})
        private String deliveryType;
        @JsonAlias({"rider_name"})
        private String riderName;
        @JsonAlias({"rider_phone"})
        private String riderPhone;
        private String remark;
        @JsonAlias({"item_summary"})
        private String itemSummary;
        @JsonAlias({"item_count"})
        private Integer itemCount;
        @JsonAlias({"items_json"})
        private JsonNode items;
        @JsonAlias({"raw_text"})
        private String rawText;
        @JsonAlias({"raw_payload", "rawPayload"})
        private JsonNode rawPayload;

        public String getPlatformOrderId() { return platformOrderId; }
        public void setPlatformOrderId(String platformOrderId) { this.platformOrderId = platformOrderId; }

        public String getPlatformOrderNo() { return platformOrderNo; }
        public void setPlatformOrderNo(String platformOrderNo) { this.platformOrderNo = platformOrderNo; }

        public String getOrderSequence() { return orderSequence; }
        public void setOrderSequence(String orderSequence) { this.orderSequence = orderSequence; }

        public String getOrderTimeText() { return orderTimeText; }
        public void setOrderTimeText(String orderTimeText) { this.orderTimeText = orderTimeText; }

        public LocalDateTime getOrderedAt() { return orderedAt; }
        public void setOrderedAt(LocalDateTime orderedAt) { this.orderedAt = orderedAt; }

        public LocalDateTime getExpectedDeliveryAt() { return expectedDeliveryAt; }
        public void setExpectedDeliveryAt(LocalDateTime expectedDeliveryAt) { this.expectedDeliveryAt = expectedDeliveryAt; }

        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

        public LocalDateTime getCancelledAt() { return cancelledAt; }
        public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

        public LocalDateTime getRefundedAt() { return refundedAt; }
        public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }

        public LocalDateTime getFetchedAt() { return fetchedAt; }
        public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getStatusText() { return statusText; }
        public void setStatusText(String statusText) { this.statusText = statusText; }

        public String getOrderType() { return orderType; }
        public void setOrderType(String orderType) { this.orderType = orderType; }

        public JsonNode getTags() { return tags; }
        public void setTags(JsonNode tags) { this.tags = tags; }

        public BigDecimal getEstimatedIncome() { return estimatedIncome; }
        public void setEstimatedIncome(BigDecimal estimatedIncome) { this.estimatedIncome = estimatedIncome; }

        public BigDecimal getCustomerPaidAmount() { return customerPaidAmount; }
        public void setCustomerPaidAmount(BigDecimal customerPaidAmount) { this.customerPaidAmount = customerPaidAmount; }

        public BigDecimal getMerchantIncome() { return merchantIncome; }
        public void setMerchantIncome(BigDecimal merchantIncome) { this.merchantIncome = merchantIncome; }

        public BigDecimal getOriginalAmount() { return originalAmount; }
        public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

        public BigDecimal getDeliveryFee() { return deliveryFee; }
        public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

        public BigDecimal getPackageFee() { return packageFee; }
        public void setPackageFee(BigDecimal packageFee) { this.packageFee = packageFee; }

        public BigDecimal getRefundAmount() { return refundAmount; }
        public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }

        public String getCustomerPhoneTail() { return customerPhoneTail; }
        public void setCustomerPhoneTail(String customerPhoneTail) { this.customerPhoneTail = customerPhoneTail; }

        public String getPrivacyPhone() { return privacyPhone; }
        public void setPrivacyPhone(String privacyPhone) { this.privacyPhone = privacyPhone; }

        public String getBackupPhone() { return backupPhone; }
        public void setBackupPhone(String backupPhone) { this.backupPhone = backupPhone; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public String getRecipientAddress() { return recipientAddress; }
        public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }

        public String getDeliveryType() { return deliveryType; }
        public void setDeliveryType(String deliveryType) { this.deliveryType = deliveryType; }

        public String getRiderName() { return riderName; }
        public void setRiderName(String riderName) { this.riderName = riderName; }

        public String getRiderPhone() { return riderPhone; }
        public void setRiderPhone(String riderPhone) { this.riderPhone = riderPhone; }

        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }

        public String getItemSummary() { return itemSummary; }
        public void setItemSummary(String itemSummary) { this.itemSummary = itemSummary; }

        public Integer getItemCount() { return itemCount; }
        public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

        public JsonNode getItems() { return items; }
        public void setItems(JsonNode items) { this.items = items; }

        public String getRawText() { return rawText; }
        public void setRawText(String rawText) { this.rawText = rawText; }

        public JsonNode getRawPayload() { return rawPayload; }
        public void setRawPayload(JsonNode rawPayload) { this.rawPayload = rawPayload; }
    }
}
