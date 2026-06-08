package com.zhirang.zhanghaoguanjia.bean.dto

data class CloneShopCreateRequest(
    val platform: String,
    val platformName: String,
    val packageName: String,
    val localVirtualUserId: Int,
    val operationKey: String,
    val autoRenew: Boolean = false
)
