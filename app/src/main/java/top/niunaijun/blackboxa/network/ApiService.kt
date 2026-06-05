package top.niunaijun.blackboxa.network

import okhttp3.MultipartBody
import retrofit2.http.*
import top.niunaijun.blackboxa.bean.dto.*

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<UserDto>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<UserDto>

    @GET("auth/me")
    suspend fun getMe(): ApiResponse<UserDto>

    @GET("shops/my")
    suspend fun getMyShops(): ApiResponse<List<ShopDto>>

    @POST("shops/report")
    suspend fun reportShop(@Body request: ShopReportRequest): ApiResponse<ShopReportResult>

    @GET("shops/validate")
    suspend fun validateShops(@Query("shopIds") shopIds: String): ApiResponse<List<ShopValidationResult>>

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
        @Part("content") content: String,
        @Part images: List<MultipartBody.Part>,
        @Part logFile: MultipartBody.Part?
    ): ApiResponse<FeedbackDto>

    @PUT("users/me/username")
    suspend fun updateUsername(@Body request: UpdateUsernameRequest): ApiResponse<UserDto>

    @PUT("users/me/password")
    suspend fun updatePassword(@Body request: ChangePasswordRequest): ApiResponse<Unit>
}
