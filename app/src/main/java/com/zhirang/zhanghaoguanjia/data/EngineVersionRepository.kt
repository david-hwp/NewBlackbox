package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.EngineVersionDto
import com.zhirang.zhanghaoguanjia.network.ApiService

class EngineVersionRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getAvailableVersions(): Result<List<EngineVersionDto>> =
        safeApiCall { api.getEngineVersions(true) }
}
