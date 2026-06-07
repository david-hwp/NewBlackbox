package com.zhirang.zhanghaoguanjia.bean.dto

data class ShopReportRequest(
    val shopName: String,
    val shopId: String,
    val platform: String,
    val platformName: String,
    val packageName: String,
    val cloneInstanceId: String? = null,
    val localVirtualUserId: Int? = null,
    val remainingDays: Int = 30,
    val autoRenew: Boolean = false
)
