package com.zhirang.zhanghaoguanjia.data

import android.content.Context
import android.net.Uri
import com.zhirang.zhanghaoguanjia.bean.dto.*
import com.zhirang.zhanghaoguanjia.network.ApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UserRepository(api: ApiService) : BaseRepository(api) {

    suspend fun login(phone: String, password: String): Result<Pair<UserDto, String>> =
        safeApiCall { api.login(LoginRequest(phone, password)) }.map { loginResponse ->
            loginResponse.user to loginResponse.token
        }

    suspend fun register(phone: String, password: String, username: String): Result<UserDto> =
        safeApiCall { api.register(RegisterRequest(phone, password, username)) }.map { loginResponse ->
            loginResponse.user.copy(token = loginResponse.token)
        }

    suspend fun getMe(): Result<UserDto> =
        safeApiCall { api.getMe() }

    suspend fun updateUsername(username: String, avatarUrl: String? = null): Result<UserDto> =
        safeApiCall { api.updateUsername(UpdateUsernameRequest(username, avatarUrl)) }

    suspend fun uploadAvatar(context: Context, uri: Uri): Result<String> =
        safeApiCall {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("无法读取头像文件")
            val mimeType = context.contentResolver.getType(uri)
                ?.takeIf { it.startsWith("image/") }
                ?: "image/jpeg"
            val extension = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                else -> "jpg"
            }
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "avatar.$extension", body)
            api.uploadFile("avatars", part)
        }.map { result ->
            result["url"] ?: throw IllegalStateException("头像上传失败")
        }

    suspend fun updatePassword(oldPwd: String, newPwd: String, confirmPwd: String): Result<Unit> =
        safeApiCall { api.updatePassword(ChangePasswordRequest(oldPwd, newPwd, confirmPwd)) }
}
