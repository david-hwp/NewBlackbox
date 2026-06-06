package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.bean.dto.LogEntryDto
import com.zhirang.zhanghaoguanjia.network.ApiService
import com.zhirang.zhanghaoguanjia.network.PagedResult

class LogRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getMyLogs(
        type: String? = null,
        page: Int = 1,
        size: Int = 20
    ): Result<PagedResult<LogEntryDto>> =
        safeApiCall { api.getMyLogs(type, page, size) }
}
