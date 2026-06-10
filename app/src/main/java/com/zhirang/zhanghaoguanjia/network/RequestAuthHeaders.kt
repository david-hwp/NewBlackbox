package com.zhirang.zhanghaoguanjia.network

import com.zhirang.zhanghaoguanjia.BuildConfig
import com.zhirang.zhanghaoguanjia.data.TokenManager
import okhttp3.Request

object RequestAuthHeaders {
    fun apply(builder: Request.Builder): Request.Builder {
        builder.header("X-Apk-Channel", BuildConfig.APK_CHANNEL)
        val token = TokenManager.getInstance().getToken()
        if (!token.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }
}
