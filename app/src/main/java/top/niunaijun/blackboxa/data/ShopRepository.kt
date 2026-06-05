package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.dto.*
import top.niunaijun.blackboxa.network.ApiService

class ShopRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getMyShopsFromApi(): Result<List<ShopDto>> =
        safeApiCall { api.getMyShops() }

    suspend fun reportShop(shop: ShopReportRequest): Result<ShopReportResult> =
        safeApiCall { api.reportShop(shop) }

    suspend fun validateShops(shopIds: List<String>): Result<List<ShopValidationResult>> =
        safeApiCall { api.validateShops(shopIds.joinToString(",")) }
}
