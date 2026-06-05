package com.zhirang.zhanghaoguanjia.bean.dto

data class EngineVersionDto(
    val id: Long,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val checksum: String?,
    val available: Boolean
)
