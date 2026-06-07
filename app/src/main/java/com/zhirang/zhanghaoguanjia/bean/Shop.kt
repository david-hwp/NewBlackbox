package com.zhirang.zhanghaoguanjia.bean

data class Shop(
    val id: Long,
    val shopName: String,
    val shopId: String,
    val platform: Platform,
    val remainingDays: Int = 0,
    val autoRenew: Boolean = false,
    val packageName: String? = null,
    val cloneInstanceId: String? = null,
    val localVirtualUserId: Int? = null,
    val icon: Any? = null,  // 占位，后续接入真实图标
    val isNew: Boolean = false
)
