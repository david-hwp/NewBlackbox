package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.AnnouncementDto
import com.zhirang.zhanghaoguanjia.network.ApiService

class AnnouncementRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getPublishedAnnouncements(type: String? = null): Result<List<AnnouncementDto>> =
        safeApiCall { api.getAnnouncements(true, type) }
}
