package com.zhirang.zhanghaoguanjia.bean.dto

data class EngineVersionDto(
    val id: Long = 0,
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val checksum: String? = null,
    val changelog: String? = null,
    val available: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
