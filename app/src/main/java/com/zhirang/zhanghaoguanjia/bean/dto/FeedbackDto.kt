package com.zhirang.zhanghaoguanjia.bean.dto

data class FeedbackDto(
    val id: Long,
    val content: String,
    val imageUrls: String?,
    val attachmentUrls: String?,
    val logUrl: String?,
    val status: String,
    val createdAt: String
)

data class FeedbackCreateRequest(
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val attachmentUrls: List<String> = emptyList(),
    val logUrl: String? = null
)
