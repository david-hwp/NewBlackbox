package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.*
import com.zhirang.zhanghaoguanjia.network.ApiService

class ShopRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getMyShopsFromApi(): Result<List<ShopDto>> =
        safeApiCall { api.getMyShops() }

    suspend fun reportShop(shop: ShopReportRequest): Result<ShopReportResult> =
        safeApiCall { api.reportShop(shop) }

    suspend fun createPendingShop(shop: ShopDto): Result<ShopDto> =
        safeApiCall { api.createPendingShop(shop) }

    suspend fun createPendingShopWithDeduction(shop: ShopReportRequest): Result<PendingShopDeductResult> =
        safeApiCall { api.createPendingShopWithDeduction(shop) }

    suspend fun updateShop(id: Long, shop: ShopDto): Result<ShopDto> =
        safeApiCall { api.updateShop(id, shop) }

    suspend fun renewShop(id: Long): Result<ShopRenewResponse> =
        safeApiCall { api.renewShop(id) }

    suspend fun deleteShop(id: Long): Result<Unit> =
        safeApiCall { api.deleteShop(id) }

    suspend fun validateShops(shopIds: List<String>): Result<List<ShopValidationResult>> =
        safeApiCall { api.validateShops(shopIds.joinToString(",")) }
}
