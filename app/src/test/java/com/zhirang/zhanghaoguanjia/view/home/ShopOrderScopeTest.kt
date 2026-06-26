package com.zhirang.zhanghaoguanjia.view.home

import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.Shop
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopOrderScopeTest {

    @Test
    fun scopeShopsReturnsOnlyAnchorPackageAndPlatform() {
        val shops = listOf(
            shop(76, "jdms", "com.jd.mrd.jingming"),
            shop(92, "mtwm", "com.sankuai.meituan.meituanwaimaibusiness"),
            shop(104, "jdms", "com.jd.mrd.jingming"),
            shop(78, "tbwm", "me.ele.napos"),
            shop(103, "jdms", "com.jd.mrd.jingming")
        )

        val scoped = ShopOrderScope.scopeShops(shops, anchorShopId = 76)

        assertEquals(listOf(76L, 104L, 103L), scoped.map { it.id })
    }

    @Test
    fun scopeShopsKeepsDraggedOrderAcrossMixedList() {
        val shops = listOf(
            shop(104, "jdms", "com.jd.mrd.jingming"),
            shop(92, "mtwm", "com.sankuai.meituan.meituanwaibusiness"),
            shop(76, "jdms", "com.jd.mrd.jingming"),
            shop(78, "tbwm", "me.ele.napos"),
            shop(103, "jdms", "com.jd.mrd.jingming")
        )

        val scoped = ShopOrderScope.scopeShops(shops, anchorShopId = 76)

        assertEquals(listOf(104L, 76L, 103L), scoped.map { it.id })
    }

    private fun shop(id: Long, platformId: String, packageName: String): Shop {
        return Shop(
            id = id,
            shopName = "shop-$id",
            shopId = "$id",
            platform = Platform.from(platformId, platformId),
            packageName = packageName
        )
    }
}
