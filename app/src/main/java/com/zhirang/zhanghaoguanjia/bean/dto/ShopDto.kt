package com.zhirang.zhanghaoguanjia.bean.dto

import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.Shop

data class ShopDto(
    val id: Long,
    val shopName: String,
    val shopId: String,
    val identityVerified: Boolean? = null,
    val platform: String,
    val platformName: String,
    val remainingDays: Int,
    val autoRenew: Boolean,
    val packageName: String?,
    val cloneInstanceId: String? = null,
    val localVirtualUserId: Int? = null,
    val hasLoginState: Boolean? = null,
    val loginStateProfile: String? = null,
    val loginStateSize: Long? = null,
    val loginStateSha256: String? = null
) {

    fun toShop(): Shop = Shop(
        id = id,
        shopName = shopName,
        shopId = shopId,
        platform = Platform.from(platform, platformName),
        remainingDays = remainingDays,
        autoRenew = autoRenew,
        packageName = packageName,
        cloneInstanceId = cloneInstanceId,
        localVirtualUserId = localVirtualUserId,
        hasLoginState = hasLoginState == true,
        loginStateProfile = loginStateProfile,
        loginStateSize = loginStateSize,
        loginStateSha256 = loginStateSha256,
        identityVerified = identityVerified == true,
        isNew = shopId.startsWith(TEMP_SHOP_ID_PREFIX)
    )

    companion object {
        const val TEMP_SHOP_ID_PREFIX = "NEW-"
    }
}
