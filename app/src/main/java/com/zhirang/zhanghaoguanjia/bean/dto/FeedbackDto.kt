package com.zhirang.zhanghaoguanjia.bean.dto

data class FeedbackDto(
    val id: Long,
    val content: String,
    val imageUrls: String?,
    val attachmentUrls: String?,
    val logUrl: String?,
    val source: String?,
    val logCaption: String?,
    val deviceInfo: String?,
    val status: String,
    val createdAt: String
)

data class FeedbackCreateRequest(
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val attachmentUrls: List<String> = emptyList(),
    val logUrl: String? = null,
    val source: String? = null,
    val logCaption: String? = null,
    val deviceInfo: String? = null
)
