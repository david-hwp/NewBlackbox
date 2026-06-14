package com.duodian.admin.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ChannelComputeAllocateRequest {
    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    @Positive(message = "分配数量必须大于0")
    @NotNull(message = "分配数量不能为空")
    private Integer amount;

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }
}
