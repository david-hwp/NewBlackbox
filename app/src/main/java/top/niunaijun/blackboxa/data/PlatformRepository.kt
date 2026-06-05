package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.dto.PlatformDto
import top.niunaijun.blackboxa.network.ApiService

class PlatformRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getPlatforms(): Result<List<PlatformDto>> =
        safeApiCall { api.getPlatforms() }
}
