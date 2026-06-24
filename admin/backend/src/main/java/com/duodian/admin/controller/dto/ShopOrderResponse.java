package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.ShopOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ShopOrderResponse {
    private Long id;
    private Long shopId;
    private Long userId;
    private Long channelId;
    private String platform;
    private String platformName;
    private String platformShopId;
    private String shopName;
    private String platformOrderId;
    private String platformOrderNo;
    private String orderSequence;
    private String source;
    private String orderTimeText;
    private LocalDateTime orderedAt;
    private LocalDateTime expectedDeliveryAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime refundedAt;
    private LocalDateTime fetchedAt;
    private LocalDateTime lastSeenAt;
    private String status;
    private String statusText;
    private String orderType;
    private BigDecimal estimatedIncome;
    private BigDecimal customerPaidAmount;
    private BigDecimal merchantIncome;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal deliveryFee;
    private BigDecimal packageFee;
    private BigDecimal refundAmount;
    private String currency;
    private String customerName;
    private String customerPhoneTail;
    private String privacyPhone;
    private String backupPhone;
    private String address;
    private String recipientAddress;
    private String deliveryType;
    private String riderName;
    private String riderPhone;
    private String remark;
    private String itemSummary;
    private Integer itemCount;
    private String itemsJson;
    private String rawText;
    private String ingestBatchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShopOrderResponse from(ShopOrder order) {
        ShopOrderResponse response = new ShopOrderResponse();
        response.setId(order.getId());
        response.setShopId(order.getShopId());
        response.setUserId(order.getUserId());
        response.setChannelId(order.getChannelId());
        response.setPlatform(order.getPlatform());
        response.setPlatformName(order.getPlatformName());
        response.setPlatformShopId(order.getPlatformShopId());
        response.setShopName(order.getShopName());
        response.setPlatformOrderId(order.getPlatformOrderId());
        response.setPlatformOrderNo(order.getPlatformOrderNo());
        response.setOrderSequence(order.getOrderSequence());
        response.setSource(order.getSource());
        response.setOrderTimeText(order.getOrderTimeText());
        response.setOrderedAt(order.getOrderedAt());
        response.setExpectedDeliveryAt(order.getExpectedDeliveryAt());
        response.setCompletedAt(order.getCompletedAt());
        response.setCancelledAt(order.getCancelledAt());
        response.setRefundedAt(order.getRefundedAt());
        response.setFetchedAt(order.getFetchedAt());
        response.setLastSeenAt(order.getLastSeenAt());
        response.setStatus(order.getStatus());
        response.setStatusText(order.getStatusText());
        response.setOrderType(order.getOrderType());
        response.setEstimatedIncome(order.getEstimatedIncome());
        response.setCustomerPaidAmount(order.getCustomerPaidAmount());
        response.setMerchantIncome(order.getMerchantIncome());
        response.setOriginalAmount(order.getOriginalAmount());
        response.setDiscountAmount(order.getDiscountAmount());
        response.setDeliveryFee(order.getDeliveryFee());
        response.setPackageFee(order.getPackageFee());
        response.setRefundAmount(order.getRefundAmount());
        response.setCurrency(order.getCurrency());
        response.setCustomerName(order.getCustomerName());
        response.setCustomerPhoneTail(order.getCustomerPhoneTail());
        response.setPrivacyPhone(order.getPrivacyPhone());
        response.setBackupPhone(order.getBackupPhone());
        response.setAddress(order.getAddress());
        response.setRecipientAddress(order.getRecipientAddress());
        response.setDeliveryType(order.getDeliveryType());
        response.setRiderName(order.getRiderName());
        response.setRiderPhone(order.getRiderPhone());
        response.setRemark(order.getRemark());
        response.setItemSummary(order.getItemSummary());
        response.setItemCount(order.getItemCount());
        response.setItemsJson(order.getItemsJson());
        response.setRawText(order.getRawText());
        response.setIngestBatchId(order.getIngestBatchId());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }
    public String getPlatformShopId() { return platformShopId; }
    public void setPlatformShopId(String platformShopId) { this.platformShopId = platformShopId; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getPlatformOrderId() { return platformOrderId; }
    public void setPlatformOrderId(String platformOrderId) { this.platformOrderId = platformOrderId; }
    public String getPlatformOrderNo() { return platformOrderNo; }
    public void setPlatformOrderNo(String platformOrderNo) { this.platformOrderNo = platformOrderNo; }
    public String getOrderSequence() { return orderSequence; }
    public void setOrderSequence(String orderSequence) { this.orderSequence = orderSequence; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
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
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
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
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getIngestBatchId() { return ingestBatchId; }
    public void setIngestBatchId(String ingestBatchId) { this.ingestBatchId = ingestBatchId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
