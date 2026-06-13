package com.duodian.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "compute_deductions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_compute_deduction_operation",
                        columnNames = {"user_id", "clone_instance_id", "deduction_type", "operation_key"}
                ),
                @UniqueConstraint(
                        name = "uk_compute_deduction_operation_key",
                        columnNames = {"user_id", "deduction_type", "operation_key"}
                )
        }
)
public class ComputeDeduction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "clone_instance_id", nullable = false, length = 255)
    private String cloneInstanceId;

    @Column(name = "deduction_type", nullable = false, length = 20)
    private String deductionType;

    @Column(name = "operation_key", nullable = false, length = 128)
    private String operationKey;

    @Column(nullable = false)
    private Integer amount = 1;

    @Column(name = "transaction_log_id")
    private Long transactionLogId;

    @Column(nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte deleted = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (deleted == null) {
            deleted = 0;
        }
        if (amount == null) {
            amount = 1;
        }
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }

    public String getCloneInstanceId() { return cloneInstanceId; }
    public void setCloneInstanceId(String cloneInstanceId) { this.cloneInstanceId = cloneInstanceId; }

    public String getDeductionType() { return deductionType; }
    public void setDeductionType(String deductionType) { this.deductionType = deductionType; }

    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public Long getTransactionLogId() { return transactionLogId; }
    public void setTransactionLogId(Long transactionLogId) { this.transactionLogId = transactionLogId; }

    public Byte getDeleted() { return deleted; }
    public void setDeleted(Byte deleted) { this.deleted = deleted == null ? 0 : deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
