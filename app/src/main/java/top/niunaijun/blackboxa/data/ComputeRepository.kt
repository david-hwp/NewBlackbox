package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.dto.GiftRequest
import top.niunaijun.blackboxa.bean.dto.GiftResult
import top.niunaijun.blackboxa.network.ApiService

class ComputeRepository(api: ApiService) : BaseRepository(api) {

    suspend fun giftCompute(toPhone: String, amount: Int): Result<GiftResult> =
        safeApiCall { api.giftCompute(GiftRequest(toPhone, amount)) }
}
