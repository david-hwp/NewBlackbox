package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.network.ApiException
import com.zhirang.zhanghaoguanjia.network.ApiResponse
import com.zhirang.zhanghaoguanjia.network.ApiService

abstract class BaseRepository(protected val api: ApiService) {

    protected suspend inline fun <T> safeApiCall(
        crossinline block: suspend () -> ApiResponse<T>
    ): Result<T> = try {
        val response = block()
        if (response.code == 200) {
            response.data?.let { Result.success(it) }
                ?: Result.success(Unit as T)
        } else {
            Result.failure(ApiException(response.code, response.message))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
