package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.AdvancedFeatureDto
import com.zhirang.zhanghaoguanjia.network.ApiService

class AdvancedFeatureRepository(api: ApiService) : BaseRepository(api) {
    suspend fun getAppAdvancedFeatures(): Result<List<AdvancedFeatureDto>> =
        safeApiCall { api.getAppAdvancedFeatures() }
}
