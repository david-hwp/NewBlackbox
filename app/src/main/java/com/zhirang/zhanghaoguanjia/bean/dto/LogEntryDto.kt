package com.zhirang.zhanghaoguanjia.bean.dto

data class LogEntryDto(
    val id: Long,
    val type: String,
    val typeText: String? = null,
    val amount: Int,
    val platform: String?,
    val shopName: String?,
    val fromPhone: String?,
    val fromName: String? = null,
    val toPhone: String?,
    val toName: String? = null,
    val remark: String?,
    val createdAt: String
)
