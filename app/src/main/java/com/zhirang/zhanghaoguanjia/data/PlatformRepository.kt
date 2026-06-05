package com.zhirang.zhanghaoguanjia.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformDto
import com.zhirang.zhanghaoguanjia.network.ApiService

class PlatformRepository(api: ApiService) : BaseRepository(api) {

    private val prefs = App.getContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    private val gson = Gson()
    private val platformListType = object : TypeToken<List<PlatformDto>>() {}.type

    suspend fun getPlatforms(): Result<List<PlatformDto>> {
        val remoteResult = safeApiCall { api.getPlatforms() }
        remoteResult.onSuccess { saveCachedPlatforms(it) }
        if (remoteResult.isSuccess) {
            return remoteResult
        }
        val cachedPlatforms = getCachedPlatforms()
        return if (cachedPlatforms.isNotEmpty()) {
            Result.success(cachedPlatforms)
        } else {
            remoteResult
        }
    }

    fun getCachedPlatforms(): List<PlatformDto> {
        val json = prefs.getString(KEY_PLATFORMS, null) ?: return emptyList()
        return try {
            gson.fromJson<List<PlatformDto>>(json, platformListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCachedPlatforms(platforms: List<PlatformDto>) {
        prefs.edit().putString(KEY_PLATFORMS, gson.toJson(platforms)).apply()
    }

    companion object {
        private const val PREFS_NAME = "platform_config_prefs"
        private const val KEY_PLATFORMS = "platforms"
    }
}
