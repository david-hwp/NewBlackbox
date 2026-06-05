package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.bean.dto.AnnouncementDto
import top.niunaijun.blackboxa.network.ApiService

class AnnouncementRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getPublishedAnnouncements(): Result<List<AnnouncementDto>> =
        safeApiCall { api.getAnnouncements(true) }
}
