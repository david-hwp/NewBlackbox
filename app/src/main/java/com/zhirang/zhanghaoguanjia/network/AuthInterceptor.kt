package com.zhirang.zhanghaoguanjia.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(RequestAuthHeaders.apply(chain.request().newBuilder()).build())
    }
}
