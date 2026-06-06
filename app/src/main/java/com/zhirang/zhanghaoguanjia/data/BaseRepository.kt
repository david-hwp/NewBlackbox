package com.zhirang.zhanghaoguanjia.data

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.network.ApiException
import com.zhirang.zhanghaoguanjia.network.ApiResponse
import com.zhirang.zhanghaoguanjia.network.ApiService
import retrofit2.HttpException

abstract class BaseRepository(protected val api: ApiService) {

    protected suspend fun <T> safeApiCall(
        redirectOnUnauthorized: Boolean = true,
        block: suspend () -> ApiResponse<T>
    ): Result<T> = try {
        val response = block()
        if (response.code == 200) {
            response.data?.let { Result.success(it) }
                ?: Result.success(Unit as T)
        } else {
            if (response.code == 401 && redirectOnUnauthorized) {
                handleUnauthorized()
            }
            Result.failure(ApiException(response.code, response.message))
        }
    } catch (e: Exception) {
        if (e is HttpException && e.code() == 401 && redirectOnUnauthorized) {
            handleUnauthorized()
        }
        Result.failure(e)
    }

    private fun handleUnauthorized() {
        TokenManager.getInstance().clearToken()
        TokenManager.getInstance().clearUser()
        val context = App.getContext()
        context.sendBroadcast(Intent(ACTION_AUTH_EXPIRED))
        if (authRedirecting) {
            return
        }
        authRedirecting = true
        Handler(Looper.getMainLooper()).post {
            val intent = Intent().apply {
                setClassName(context, "com.zhirang.zhanghaoguanjia.view.login.LoginActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    companion object {
        const val ACTION_AUTH_EXPIRED = "com.zhirang.zhanghaoguanjia.action.AUTH_EXPIRED"
        @Volatile
        private var authRedirecting = false

        fun clearAuthRedirecting() {
            authRedirecting = false
        }
    }
}
