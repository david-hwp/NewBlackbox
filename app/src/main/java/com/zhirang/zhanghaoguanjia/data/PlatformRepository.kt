package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.PlatformDto
import com.zhirang.zhanghaoguanjia.network.ApiService

class PlatformRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getPlatforms(): Result<List<PlatformDto>> =
        safeApiCall { api.getPlatforms() }
}
