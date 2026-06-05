package top.niunaijun.blackboxa.data

import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.bean.dto.FeedbackDto
import top.niunaijun.blackboxa.network.ApiService
import java.io.File

class FeedbackRepository(api: ApiService) : BaseRepository(api) {

    suspend fun submitFeedback(
        content: String,
        imageUris: List<Uri>,
        attachmentUris: List<Uri>,
        logFile: File?
    ): Result<FeedbackDto> {
        val context = App.getContext()
        val imageParts = imageUris.mapIndexedNotNull { index, uri ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapIndexedNotNull null
            val mimeType = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("images", "image-$index.$extension", body)
        }
        val attachmentParts = attachmentUris.mapIndexedNotNull { index, uri ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapIndexedNotNull null
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("attachments", "attachment-$index", body)
        }
        val logPart = logFile?.takeIf { it.exists() }?.let {
            val body = it.asRequestBody("application/zip".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("logFile", it.name, body)
        }
        val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
        return safeApiCall { api.submitFeedback(contentBody, imageParts, attachmentParts, logPart) }
    }
}
