package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.*
import com.zhirang.zhanghaoguanjia.network.ApiService
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ShopRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getMyShopsFromApi(): Result<List<ShopDto>> =
        safeApiCall { api.getMyShops() }

    suspend fun reportShop(shop: ShopReportRequest): Result<ShopReportResult> =
        safeApiCall { api.reportShop(shop) }

    suspend fun createCloneShop(request: CloneShopCreateRequest): Result<CloneShopCreateResult> =
        safeApiCall { api.createCloneShop(request) }

    suspend fun createPendingShopWithDeduction(shop: ShopReportRequest): Result<PendingShopDeductResult> =
        safeApiCall { api.createPendingShopWithDeduction(shop) }

    suspend fun updateShop(id: Long, shop: ShopDto): Result<ShopDto> =
        safeApiCall { api.updateShop(id, shop) }

    suspend fun reorderShops(shopIds: List<Long>): Result<List<ShopDto>> =
        safeApiCall { api.reorderShops(ShopOrderRequest(shopIds)) }

    suspend fun renewShop(id: Long, request: ShopRenewRequest): Result<ShopRenewResponse> =
        safeApiCall { api.renewShop(id, request) }

    suspend fun issueShopAuthToken(id: Long, request: ShopAuthTokenRequest): Result<CloneShopCreateResult> =
        safeApiCall { api.issueShopAuthToken(id, request) }

    suspend fun uploadLoginState(id: Long, profile: String, manifest: String, artifact: ByteArray): Result<ShopDto> =
        safeApiCall {
            val body = artifact.toRequestBody("application/zip".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "login-state.zip", body)
            api.uploadShopLoginState(
                id,
                part,
                profile.toRequestBody("text/plain".toMediaTypeOrNull()),
                manifest.toRequestBody("application/json".toMediaTypeOrNull())
            )
        }

    suspend fun uploadLoginStateFile(id: Long, profile: String, manifest: String, artifact: File): Result<ShopDto> =
        safeApiCall {
            val body = artifact.asRequestBody("application/zip".toMediaType())
            val part = MultipartBody.Part.createFormData("file", "login-state.zip", body)
            api.uploadShopLoginState(
                id,
                part,
                profile.toRequestBody("text/plain".toMediaTypeOrNull()),
                manifest.toRequestBody("application/json".toMediaTypeOrNull())
            )
        }

    suspend fun downloadLoginState(id: Long): Result<LoginStateDownload?> = try {
        val request = Request.Builder()
            .url(RetrofitClient.resolveUrl("shops/$id/login-state"))
            .get()
            .build()
        RetrofitClient.execute(request).use { response ->
            when {
                response.code == 204 -> Result.success(null)
                response.isSuccessful -> {
                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        Result.success(null)
                    } else {
                        Result.success(
                            LoginStateDownload(
                                profile = response.header("X-Login-State-Profile")?.takeIf { it.isNotBlank() },
                                sha256 = response.header("X-Login-State-Sha256")?.takeIf { it.isNotBlank() },
                                bytes = bytes
                            )
                        )
                    }
                }
                response.code == 401 -> Result.failure(Exception("未登录"))
                else -> Result.failure(Exception("下载登录态失败: ${response.code}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteShop(id: Long): Result<Unit> =
        safeApiCall { api.deleteShop(id) }

    suspend fun validateShops(shopIds: List<String>, packageName: String? = null): Result<List<ShopValidationResult>> =
        safeApiCall { api.validateShops(shopIds.joinToString(","), packageName) }

    data class LoginStateDownload(
        val profile: String?,
        val sha256: String?,
        val bytes: ByteArray
    )
}
