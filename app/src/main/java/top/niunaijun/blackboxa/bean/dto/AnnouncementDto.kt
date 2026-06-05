package top.niunaijun.blackboxa.bean.dto

data class AnnouncementDto(
    val id: Long,
    val title: String,
    val content: String,
    val published: Boolean,
    val createdAt: String?,
    val updatedAt: String?
)
