package top.niunaijun.blackboxa.data

import android.net.Uri
import okhttp3.MultipartBody
import top.niunaijun.blackboxa.bean.dto.FeedbackDto
import top.niunaijun.blackboxa.network.ApiService

class FeedbackRepository(api: ApiService) : BaseRepository(api) {

    suspend fun submitFeedback(
        content: String,
        imageUris: List<Uri>,
        logUri: Uri?
    ): Result<FeedbackDto> {
        // MVP 阶段：仅上传文本，图片和日志文件后续实现
        val parts = emptyList<MultipartBody.Part>()
        val logPart: MultipartBody.Part? = null
        return safeApiCall { api.submitFeedback(content, parts, logPart) }
    }
}
