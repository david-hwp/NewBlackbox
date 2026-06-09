package com.duodian.admin.controller.dto;

import com.duodian.admin.entity.TransactionLog;
import com.duodian.admin.entity.User;

import java.time.LocalDateTime;

public class TransactionLogResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userPhone;
    private Long channelId;
    private String channelCode;
    private String channelName;
    private Long relatedLogId;
    private String type;
    private Integer amount;
    private String platform;
    private String shopName;
    private String fromPhone;
    private String fromName;
    private String toPhone;
    private String toName;
    private String remark;
    private LocalDateTime createdAt;

    public static TransactionLogResponse from(TransactionLog log, User user) {
        TransactionLogResponse response = new TransactionLogResponse();
        response.setId(log.getId());
        response.setUserId(log.getUserId());
        response.setUserName(user != null ? user.getUsername() : null);
        response.setUserPhone(user != null ? user.getPhone() : null);
        response.setChannelId(log.getChannelId());
        response.setRelatedLogId(log.getRelatedLogId());
        response.setType(log.getType());
        response.setAmount(log.getAmount());
        response.setPlatform(log.getPlatform());
        response.setShopName(log.getShopName());
        response.setFromPhone(log.getFromPhone());
        response.setFromName(log.getFromName());
        response.setToPhone(log.getToPhone());
        response.setToName(log.getToName());
        response.setRemark(log.getRemark());
        response.setCreatedAt(log.getCreatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public Long getRelatedLogId() { return relatedLogId; }
    public void setRelatedLogId(Long relatedLogId) { this.relatedLogId = relatedLogId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getFromPhone() { return fromPhone; }
    public void setFromPhone(String fromPhone) { this.fromPhone = fromPhone; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getToPhone() { return toPhone; }
    public void setToPhone(String toPhone) { this.toPhone = toPhone; }

    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
