package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.dto.EngineVersionDto
import top.niunaijun.blackboxa.network.ApiService

class EngineVersionRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getAvailableVersions(): Result<List<EngineVersionDto>> =
        safeApiCall { api.getEngineVersions(true) }
}
