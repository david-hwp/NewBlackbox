package com.zhirang.zhanghaoguanjia.bean.dto

data class ShopReportRequest(
    val systemShopId: Long? = null,
    val shopName: String,
    val shopId: String,
    val platform: String,
    val platformName: String,
    val packageName: String,
    val cloneInstanceId: String? = null,
    val localVirtualUserId: Int? = null,
    val remainingDays: Int = 30,
    val autoRenew: Boolean = false,
    val confirmIdentityBinding: Boolean = false
)
