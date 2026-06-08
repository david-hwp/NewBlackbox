package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.AppVersionDto
import com.zhirang.zhanghaoguanjia.network.ApiService

class AppVersionRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getPublishedVersions(): Result<List<AppVersionDto>> =
        safeApiCall { api.getAppVersions(true) }
}
