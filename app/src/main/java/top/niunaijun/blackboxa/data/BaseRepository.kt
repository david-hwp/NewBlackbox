package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.network.ApiException
import top.niunaijun.blackboxa.network.ApiResponse
import top.niunaijun.blackboxa.network.ApiService

abstract class BaseRepository(protected val api: ApiService) {

    protected suspend inline fun <T> safeApiCall(
        crossinline block: suspend () -> ApiResponse<T>
    ): Result<T> = try {
        val response = block()
        if (response.code == 200) {
            response.data?.let { Result.success(it) }
                ?: Result.failure(Exception("Empty response data"))
        } else {
            Result.failure(ApiException(response.code, response.message))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
