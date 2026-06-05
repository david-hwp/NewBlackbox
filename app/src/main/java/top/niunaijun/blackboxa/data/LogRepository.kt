package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.dto.LogEntryDto
import top.niunaijun.blackboxa.network.ApiService
import top.niunaijun.blackboxa.network.PagedResult

class LogRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getMyLogs(
        type: String? = null,
        page: Int = 1,
        size: Int = 20
    ): Result<PagedResult<LogEntryDto>> =
        safeApiCall { api.getMyLogs(type, page, size) }
}
