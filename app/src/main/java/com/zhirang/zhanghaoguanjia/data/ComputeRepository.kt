package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.GiftRequest
import com.zhirang.zhanghaoguanjia.bean.dto.GiftResult
import com.zhirang.zhanghaoguanjia.bean.dto.ComputeReclaimRequest
import com.zhirang.zhanghaoguanjia.bean.dto.ComputeReclaimResult
import com.zhirang.zhanghaoguanjia.network.ApiService

class ComputeRepository(api: ApiService) : BaseRepository(api) {

    suspend fun giftCompute(toPhone: String, amount: Int): Result<GiftResult> =
        safeApiCall { api.giftCompute(GiftRequest(toPhone, amount)) }

    suspend fun giftPhoneMinutes(toPhone: String, amount: Int): Result<GiftResult> =
        safeApiCall { api.giftPhoneMinutes(GiftRequest(toPhone, amount)) }

    suspend fun queryReclaimableCompute(toPhone: String): Result<ComputeReclaimResult> =
        safeApiCall { api.getLatestReclaimableCompute(toPhone) }

    suspend fun queryReclaimablePhoneMinutes(toPhone: String): Result<ComputeReclaimResult> =
        safeApiCall { api.getLatestReclaimablePhoneMinutes(toPhone) }

    suspend fun reclaimCompute(
        toPhone: String,
        giftLogId: Long?,
        amount: Int
    ): Result<ComputeReclaimResult> =
        safeApiCall { api.reclaimCompute(ComputeReclaimRequest(toPhone, giftLogId, amount)) }

    suspend fun reclaimPhoneMinutes(
        toPhone: String,
        giftLogId: Long?,
        amount: Int
    ): Result<ComputeReclaimResult> =
        safeApiCall { api.reclaimPhoneMinutes(ComputeReclaimRequest(toPhone, giftLogId, amount)) }
}
