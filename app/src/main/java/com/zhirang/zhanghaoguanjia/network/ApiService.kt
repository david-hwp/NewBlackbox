package com.zhirang.zhanghaoguanjia.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*
import com.zhirang.zhanghaoguanjia.bean.dto.*

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<Unit>

    @GET("auth/me")
    suspend fun getMe(): ApiResponse<UserDto>

    @GET("platforms")
    suspend fun getPlatforms(): ApiResponse<List<PlatformDto>>

    @GET("engine-versions")
    suspend fun getEngineVersions(@Query("available") available: Boolean? = null): ApiResponse<List<EngineVersionDto>>

    @POST("engine-versions/verify")
    suspend fun verifyEnginePackage(@Body request: PackageVerifyRequest): ApiResponse<PackageVerifyResponse>

    @GET("app-versions")
    suspend fun getAppVersions(@Query("published") published: Boolean? = null): ApiResponse<List<AppVersionDto>>

    @POST("app-versions/verify")
    suspend fun verifyAppPackage(@Body request: PackageVerifyRequest): ApiResponse<PackageVerifyResponse>

    @GET("announcements")
    suspend fun getAnnouncements(
        @Query("published") published: Boolean? = null,
        @Query("type") type: String? = null
    ): ApiResponse<List<AnnouncementDto>>

    @GET("shops/my")
    suspend fun getMyShops(): ApiResponse<List<ShopDto>>

    @POST("shops/report")
    suspend fun reportShop(@Body request: ShopReportRequest): ApiResponse<ShopReportResult>

    @POST("shops/clone/create")
    suspend fun createCloneShop(@Body request: CloneShopCreateRequest): ApiResponse<CloneShopCreateResult>

    @POST("shops/pending-deduct")
    suspend fun createPendingShopWithDeduction(@Body request: ShopReportRequest): ApiResponse<PendingShopDeductResult>

    @PUT("shops/{id}")
    suspend fun updateShop(@Path("id") id: Long, @Body request: ShopDto): ApiResponse<ShopDto>

    @POST("shops/{id}/renew")
    suspend fun renewShop(@Path("id") id: Long, @Body request: ShopRenewRequest): ApiResponse<ShopRenewResponse>

    @POST("shops/{id}/auth-token")
    suspend fun issueShopAuthToken(
        @Path("id") id: Long,
        @Body request: ShopAuthTokenRequest
    ): ApiResponse<CloneShopCreateResult>

    @DELETE("shops/{id}")
    suspend fun deleteShop(@Path("id") id: Long): ApiResponse<Unit>

    @GET("shops/validate")
    suspend fun validateShops(
        @Query("shopIds") shopIds: String,
        @Query("packageName") packageName: String? = null
    ): ApiResponse<List<ShopValidationResult>>

    @POST("compute/gift")
    suspend fun giftCompute(@Body request: GiftRequest): ApiResponse<GiftResult>

    @GET("logs/my")
    suspend fun getMyLogs(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<PagedResult<LogEntryDto>>

    @Multipart
    @POST("feedbacks")
    suspend fun submitFeedback(
        @Part("content") content: RequestBody,
        @Part images: List<MultipartBody.Part>,
        @Part attachments: List<MultipartBody.Part>,
        @Part logFile: MultipartBody.Part?
    ): ApiResponse<FeedbackDto>

    @POST("feedbacks")
    suspend fun createFeedback(@Body request: FeedbackCreateRequest): ApiResponse<FeedbackDto>

    @PUT("users/me/username")
    suspend fun updateUsername(@Body request: UpdateUsernameRequest): ApiResponse<UserDto>

    @Multipart
    @POST("files/{type}")
    suspend fun uploadFile(
        @Path("type") type: String,
        @Part file: MultipartBody.Part
    ): ApiResponse<Map<String, String>>

    @PUT("users/me/password")
    suspend fun updatePassword(@Body request: ChangePasswordRequest): ApiResponse<Unit>
}
