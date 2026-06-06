package com.zhirang.zhanghaoguanjia.bean.dto

data class PendingShopDeductResult(
    val shop: ShopDto,
    val balance: Int,
    val shopCount: Int? = null,
    val platformCount: Int? = null,
    val deducted: Boolean = true
)
