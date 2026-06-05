package com.zhirang.zhanghaoguanjia.util

import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformItemDto

object PlatformRegistry {
    @Volatile
    private var platformsById: Map<String, PlatformItemDto> = emptyMap()

    fun update(platforms: List<PlatformItemDto>) {
        platformsById = platforms.associateBy { it.platform.id }
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
}
