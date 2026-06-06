package com.zhirang.zhanghaoguanjia.bean.dto

import com.zhirang.zhanghaoguanjia.bean.Platform

data class PlatformDto(
    val id: String,
    val name: String,
    val packageName: String?,
    val iconUrl: String?,
    val available: Boolean
) {
    fun toPlatformItem(): PlatformItemDto {
        val platform = Platform.from(id, name)
        return PlatformItemDto(
            platform = platform,
            displayName = name.ifBlank { platform.displayName },
            packageName = packageName?.takeIf { it.isNotBlank() },
            iconKey = iconUrl ?: id,
            available = available
        )
    }
}

data class PlatformItemDto(
    val platform: Platform,
    val displayName: String,
    val packageName: String?,
    val iconKey: String,
    val available: Boolean
)
