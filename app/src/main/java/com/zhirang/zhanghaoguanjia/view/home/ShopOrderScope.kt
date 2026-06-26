package com.zhirang.zhanghaoguanjia.view.home

import com.zhirang.zhanghaoguanjia.bean.Shop

object ShopOrderScope {
    fun sameScope(left: Shop, right: Shop): Boolean {
        return normalize(left.packageName) == normalize(right.packageName)
                && normalize(left.platform.id) == normalize(right.platform.id)
    }

    fun scopeShops(shops: List<Shop>, anchorShopId: Long?): List<Shop> {
        val anchor = anchorShopId?.let { id -> shops.firstOrNull { it.id == id } } ?: return emptyList()
        return shops.filter { sameScope(anchor, it) }
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
