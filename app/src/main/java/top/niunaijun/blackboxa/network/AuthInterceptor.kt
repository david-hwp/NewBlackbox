package top.niunaijun.blackboxa.network

import okhttp3.Interceptor
import okhttp3.Response
import top.niunaijun.blackboxa.data.TokenManager

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = TokenManager.getInstance().getToken()

        val newRequest = if (!token.isNullOrEmpty()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        return chain.proceed(newRequest)
    }
}
