package com.zhirang.zhanghaoguanjia.bean.dto

data class ComputeReclaimRequest(
    val toPhone: String,
    val giftLogId: Long?,
    val amount: Int
)

data class ComputeReclaimResult(
    val giftLogId: Long,
    val toPhone: String,
    val toName: String? = null,
    val giftAmount: Int = 0,
    val giftCreatedAt: String? = null,
    val receiverConsumedAmount: Int = 0,
    val alreadyReclaimedAmount: Int = 0,
    val reclaimableAmount: Int = 0,
    val fromBalance: Int? = null,
    val toBalance: Int? = null,
    val reclaimedAmount: Int? = null
)
