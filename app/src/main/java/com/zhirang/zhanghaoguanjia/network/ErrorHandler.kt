package com.zhirang.zhanghaoguanjia.network

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.view.login.LoginActivity
import java.io.IOException

object ErrorHandler {

    fun handle(context: Context, throwable: Throwable) {
        val message = when (throwable) {
            is ApiException -> when (throwable.code) {
                401 -> {
                    TokenManager.getInstance().clearToken()
                    TokenManager.getInstance().clearUser()
                    val intent = Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    "登录已过期，请重新登录"
                }
                402 -> "算力不足，请联系管理员充值"
                404 -> "请求的资源不存在"
                else -> throwable.message ?: "请求失败"
            }
            is IOException -> "网络异常，请稍后重试"
            else -> throwable.message ?: "未知错误"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
