package com.zhirang.zhanghaoguanjia.bean.dto

data class CloneShopCreateResult(
    val shop: ShopDto,
    val balance: Int,
    val shopCount: Int? = null,
    val platformCount: Int? = null,
    val deducted: Boolean = true,
    val authorizationToken: String,
    val publicKeyId: String? = null,
    val authStartAt: String? = null,
    val authExpireAt: String? = null
)
