package com.zhirang.zhanghaoguanjia.bean.dto

data class ShopRenewResponse(
    val shop: ShopDto,
    val balance: Int,
    val shopCount: Int? = null,
    val platformCount: Int? = null
)
