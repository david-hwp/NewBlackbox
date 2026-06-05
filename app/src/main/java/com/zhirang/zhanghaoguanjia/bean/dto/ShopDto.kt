package com.zhirang.zhanghaoguanjia.bean.dto

import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.Shop

data class ShopDto(
    val id: Long,
    val shopName: String,
    val shopId: String,
    val platform: String,
    val platformName: String,
    val remainingDays: Int,
    val autoRenew: Boolean,
    val packageName: String?,
    val cloneInstanceId: String? = null
) {

    fun toShop(): Shop = Shop(
        id = id,
        shopName = shopName,
        shopId = shopId,
        platform = Platform.fromId(platform),
        remainingDays = remainingDays,
        autoRenew = autoRenew,
        packageName = packageName,
        cloneInstanceId = cloneInstanceId,
        isNew = shopId.startsWith(TEMP_SHOP_ID_PREFIX)
    )

    companion object {
        const val TEMP_SHOP_ID_PREFIX = "NEW-"
    }
}
