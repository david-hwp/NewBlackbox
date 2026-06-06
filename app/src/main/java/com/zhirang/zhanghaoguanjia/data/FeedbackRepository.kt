package com.zhirang.zhanghaoguanjia.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.dto.FeedbackCreateRequest
import com.zhirang.zhanghaoguanjia.bean.dto.FeedbackDto
import com.zhirang.zhanghaoguanjia.network.ApiService
import java.io.ByteArrayOutputStream
import java.io.File

class FeedbackRepository(api: ApiService) : BaseRepository(api) {

    suspend fun uploadFeedbackImage(uri: Uri): Result<String> =
        safeApiCall {
            val bytes = compressImage(uri)
            val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val fileName = "feedback-image-${System.currentTimeMillis()}.jpg"
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            api.uploadFile("feedback-images", part)
        }.map { result ->
            result["url"] ?: throw IllegalStateException("图片上传失败")
        }

    suspend fun uploadFeedbackLog(logFile: File): Result<String> =
        safeApiCall {
            val body = logFile.asRequestBody("application/zip".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", logFile.name, body)
            api.uploadFile("feedback-logs", part)
        }.map { result ->
            result["url"] ?: throw IllegalStateException("日志上传失败")
        }

    suspend fun submitFeedback(
        content: String,
        imageUrls: List<String>,
        logUrl: String?,
        logCaption: String? = null,
        deviceInfo: String? = null
    ): Result<FeedbackDto> {
        return safeApiCall {
            api.createFeedback(
                FeedbackCreateRequest(
                    content = content,
                    imageUrls = imageUrls,
                    logUrl = logUrl,
                    source = "APP",
                    logCaption = logCaption,
                    deviceInfo = deviceInfo
                )
            )
        }
    }

    @Deprecated("Use uploadFeedbackImage/uploadFeedbackLog plus JSON submitFeedback.")
    suspend fun submitMultipartFeedback(
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

    private fun compressImage(uri: Uri): ByteArray {
        val context = App.getContext()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val sampleSize = calculateInSampleSize(bounds, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalArgumentException("无法读取图片")

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 1600
        private const val JPEG_QUALITY = 82
    }
}
