package com.zhirang.zhanghaoguanjia.bean.dto

data class LoginRequest(
    val phone: String,
    val password: String,
    val apkChannel: String? = null
)
