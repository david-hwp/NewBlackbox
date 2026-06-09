package com.zhirang.zhanghaoguanjia.network

import okhttp3.Interceptor
import okhttp3.Response
import com.zhirang.zhanghaoguanjia.BuildConfig
import com.zhirang.zhanghaoguanjia.data.TokenManager

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = TokenManager.getInstance().getToken()

        val builder = request.newBuilder()
            .header("X-Apk-Channel", BuildConfig.APK_CHANNEL)
        if (!token.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}
