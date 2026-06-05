package top.niunaijun.blackboxa.bean.dto

import top.niunaijun.blackboxa.bean.Platform

data class PlatformDto(
    val id: String,
    val name: String,
    val packageName: String?,
    val iconUrl: String?,
    val available: Boolean
) {
    fun toPlatformItem(): PlatformItemDto {
        val platform = Platform.fromId(id)
        return PlatformItemDto(
            platform = platform,
            displayName = name.ifBlank { platform.displayName },
            packageName = packageName ?: platform.packageName,
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
