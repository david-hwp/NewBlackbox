package com.zhirang.zhanghaoguanjia.bean.dto

data class ShopValidationResult(
    val shopId: String,
    val isValid: Boolean,
    val expireAt: String,
    val autoRenew: Boolean
)
