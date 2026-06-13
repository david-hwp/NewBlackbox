package com.zhirang.zhanghaoguanjia.bean.dto

data class AppVersionDto(
    val id: Long = 0,
    val versionCode: Int = 0,
    val versionName: String = "",
    val applicationId: String? = null,
    val apkUrl: String = "",
    val checksum: String? = null,
    val fileSize: Long? = null,
    val changelog: String? = null,
    val published: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
