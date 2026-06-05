package com.duodian.admin.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class GiftRequest {
    @NotBlank(message = "对方手机号不能为空")
    private String toPhone;

    @Positive(message = "赠送数量必须大于0")
    private Integer amount;

    public String getToPhone() { return toPhone; }
    public void setToPhone(String toPhone) { this.toPhone = toPhone; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
}
