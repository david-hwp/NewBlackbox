package com.duodian.admin.controller.dto;

import java.time.LocalDateTime;

public class ComputeReclaimResponse {
    private Long giftLogId;
    private String toPhone;
    private String toName;
    private Integer giftAmount;
    private LocalDateTime giftCreatedAt;
    private Integer receiverConsumedAmount;
    private Integer alreadyReclaimedAmount;
    private Integer reclaimableAmount;
    private Integer fromBalance;
    private Integer toBalance;
    private Integer reclaimedAmount;

    public Long getGiftLogId() { return giftLogId; }
    public void setGiftLogId(Long giftLogId) { this.giftLogId = giftLogId; }

    public String getToPhone() { return toPhone; }
    public void setToPhone(String toPhone) { this.toPhone = toPhone; }

    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }

    public Integer getGiftAmount() { return giftAmount; }
    public void setGiftAmount(Integer giftAmount) { this.giftAmount = giftAmount; }

    public LocalDateTime getGiftCreatedAt() { return giftCreatedAt; }
    public void setGiftCreatedAt(LocalDateTime giftCreatedAt) { this.giftCreatedAt = giftCreatedAt; }

    public Integer getReceiverConsumedAmount() { return receiverConsumedAmount; }
    public void setReceiverConsumedAmount(Integer receiverConsumedAmount) { this.receiverConsumedAmount = receiverConsumedAmount; }

    public Integer getAlreadyReclaimedAmount() { return alreadyReclaimedAmount; }
    public void setAlreadyReclaimedAmount(Integer alreadyReclaimedAmount) { this.alreadyReclaimedAmount = alreadyReclaimedAmount; }

    public Integer getReclaimableAmount() { return reclaimableAmount; }
    public void setReclaimableAmount(Integer reclaimableAmount) { this.reclaimableAmount = reclaimableAmount; }

    public Integer getFromBalance() { return fromBalance; }
    public void setFromBalance(Integer fromBalance) { this.fromBalance = fromBalance; }

    public Integer getToBalance() { return toBalance; }
    public void setToBalance(Integer toBalance) { this.toBalance = toBalance; }

    public Integer getReclaimedAmount() { return reclaimedAmount; }
    public void setReclaimedAmount(Integer reclaimedAmount) { this.reclaimedAmount = reclaimedAmount; }
}
