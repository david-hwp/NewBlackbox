package com.duodian.admin.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ComputeReclaimRequest {
    @NotBlank(message = "对方手机号不能为空")
    private String toPhone;

    private Long giftLogId;

    @Positive(message = "取回数量必须大于0")
    @NotNull(message = "取回数量不能为空")
    private Integer amount;

    public String getToPhone() { return toPhone; }
    public void setToPhone(String toPhone) { this.toPhone = toPhone; }

    public Long getGiftLogId() { return giftLogId; }
    public void setGiftLogId(Long giftLogId) { this.giftLogId = giftLogId; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
}
