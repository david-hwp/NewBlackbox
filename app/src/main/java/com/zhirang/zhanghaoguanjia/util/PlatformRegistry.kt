package com.zhirang.zhanghaoguanjia.util

import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformItemDto

object PlatformRegistry {
    @Volatile
    private var platformsById: Map<String, PlatformItemDto> = emptyMap()
    @Volatile
    private var platformsByPackage: Map<String, List<PlatformItemDto>> = emptyMap()

    fun update(platforms: List<PlatformItemDto>) {
        platformsById = platforms.associateBy { it.platform.id }
        platformsByPackage = platforms
            .mapNotNull { item -> item.packageName?.takeIf { it.isNotBlank() }?.let { it to item } }
            .groupBy({ it.first }, { it.second })
    }

    fun get(platform: Platform): PlatformItemDto? = platformsById[platform.id]

    fun get(platformId: String?): PlatformItemDto? {
        if (platformId.isNullOrBlank()) {
            return null
        }
        return platformsById[platformId]
    }

    fun isAvailable(platform: Platform): Boolean = get(platform)?.available ?: false

    fun isAvailable(platformId: String?): Boolean = get(platformId)?.available ?: false

    fun displayName(platform: Platform): String = get(platform)?.displayName ?: platform.displayName

    fun packageName(platform: Platform): String? = get(platform)?.packageName

    fun platformsForPackage(packageName: String?): List<PlatformItemDto> {
        if (packageName.isNullOrBlank()) {
            return emptyList()
        }
        return platformsByPackage[packageName].orEmpty()
    }

    fun preferredPlatformForPackage(packageName: String?): PlatformItemDto? {
        return platformsForPackage(packageName).firstOrNull { it.available }
            ?: platformsForPackage(packageName).firstOrNull()
    }
}
