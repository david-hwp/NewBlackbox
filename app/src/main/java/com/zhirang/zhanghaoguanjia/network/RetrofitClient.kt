package com.zhirang.zhanghaoguanjia.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.zhirang.zhanghaoguanjia.BuildConfig
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val BASE_URL = BuildConfig.API_BASE_URL.ensureTrailingSlash()
    private val SERVER_ROOT = BASE_URL.removeSuffix("/api/")

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    fun resolveUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        val normalized = url.trimStart('/')
        return if (normalized.startsWith("api/")) {
            "$SERVER_ROOT/$normalized"
        } else {
            BASE_URL + normalized
        }
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}
