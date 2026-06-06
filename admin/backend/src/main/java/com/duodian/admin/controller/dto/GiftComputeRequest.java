package com.duodian.admin.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class GiftComputeRequest {
    @NotNull(message = "转出用户ID不能为空")
    private Long fromUserId;

    @NotBlank(message = "对方手机号不能为空")
    private String toPhone;

    @Positive(message = "赠送数量必须大于0")
    @NotNull(message = "赠送数量不能为空")
    private Integer amount;

    // Getters and Setters
    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }

    public String getToPhone() { return toPhone; }
    public void setToPhone(String toPhone) { this.toPhone = toPhone; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
}
