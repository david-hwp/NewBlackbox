package com.zhirang.zhanghaoguanjia.bean.dto

data class AnnouncementDto(
    val id: Long,
    val title: String,
    val content: String,
    val type: String? = "NORMAL",
    val published: Boolean,
    val createdAt: String?,
    val updatedAt: String?
)
