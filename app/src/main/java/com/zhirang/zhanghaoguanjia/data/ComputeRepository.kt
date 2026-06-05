package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.GiftRequest
import com.zhirang.zhanghaoguanjia.bean.dto.GiftResult
import com.zhirang.zhanghaoguanjia.network.ApiService

class ComputeRepository(api: ApiService) : BaseRepository(api) {

    suspend fun giftCompute(toPhone: String, amount: Int): Result<GiftResult> =
        safeApiCall { api.giftCompute(GiftRequest(toPhone, amount)) }
}
