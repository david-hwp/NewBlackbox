package com.zhirang.zhanghaoguanjia.bean.dto

data class PackageVerifyRequest(
    val versionCode: Int,
    val md5: String,
    val sha256: String,
    val packageName: String? = null
)
