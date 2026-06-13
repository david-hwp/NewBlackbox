package com.zhirang.zhanghaoguanjia.data

import android.content.Context
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.Shop

object LocalShopIdentityStore {
    private const val PREFS_NAME = "local_shop_identity"

    private val prefs by lazy {
        App.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun read(userId: Long, shop: Shop): Boolean? {
        val key = key(userId, shop) ?: return null
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun mark(userId: Long, shop: Shop, packageName: String, localVirtualUserId: Int, verified: Boolean) {
        val key = key(
            userId = userId,
            systemShopId = shop.id,
            packageName = packageName,
            cloneInstanceId = shop.cloneInstanceId,
            localVirtualUserId = localVirtualUserId
        ) ?: return
        prefs.edit().putBoolean(key, verified).apply()
    }

    fun apply(userId: Long, shop: Shop): Shop {
        val localVerified = read(userId, shop) ?: return shop
        return shop.copy(localIdentityVerified = localVerified)
    }

    private fun key(userId: Long, shop: Shop): String? {
        return key(
            userId = userId,
            systemShopId = shop.id,
            packageName = shop.packageName,
            cloneInstanceId = shop.cloneInstanceId,
            localVirtualUserId = shop.localVirtualUserId
        )
    }

    private fun key(
        userId: Long,
        systemShopId: Long,
        packageName: String?,
        cloneInstanceId: String?,
        localVirtualUserId: Int?
    ): String? {
        val normalizedPackageName = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedCloneId = cloneInstanceId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedUserId = localVirtualUserId?.takeIf { it >= 0 } ?: return null
        return "$userId:$systemShopId:$normalizedPackageName:$normalizedCloneId:$normalizedUserId"
    }
}
