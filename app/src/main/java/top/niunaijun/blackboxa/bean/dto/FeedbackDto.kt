package top.niunaijun.blackboxa.bean.dto

data class FeedbackDto(
    val id: Long,
    val content: String,
    val imageUrls: String?,
    val attachmentUrls: String?,
    val logUrl: String?,
    val status: String,
    val createdAt: String
)
