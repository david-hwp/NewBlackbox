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
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false)
        }
        val legacyKey = legacyKey(userId, shop) ?: return null
        return if (prefs.contains(legacyKey)) prefs.getBoolean(legacyKey, false) else null
    }

    fun mark(userId: Long, shop: Shop, packageName: String, @Suppress("UNUSED_PARAMETER") localVirtualUserId: Int, verified: Boolean) {
        val key = key(
            userId = userId,
            systemShopId = shop.id,
            packageName = packageName,
            cloneInstanceId = shop.cloneInstanceId
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
            cloneInstanceId = shop.cloneInstanceId
        )
    }

    private fun legacyKey(userId: Long, shop: Shop): String? {
        val normalizedPackageName = shop.packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedCloneId = shop.cloneInstanceId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedUserId = shop.localVirtualUserId?.takeIf { it >= 0 } ?: return null
        return "$userId:${shop.id}:$normalizedPackageName:$normalizedCloneId:$normalizedUserId"
    }

    private fun key(
        userId: Long,
        systemShopId: Long,
        packageName: String?,
        cloneInstanceId: String?
    ): String? {
        val normalizedPackageName = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedCloneId = cloneInstanceId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$userId:$systemShopId:$normalizedPackageName:$normalizedCloneId"
    }
}
