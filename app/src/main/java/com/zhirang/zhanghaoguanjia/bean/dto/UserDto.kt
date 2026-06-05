package com.zhirang.zhanghaoguanjia.bean.dto

data class UserDto(
    val id: Long,
    val username: String,
    val avatarUrl: String? = null,
    val phone: String,
    val role: String,
    val computeBalance: Int,
    val shopCount: Int,
    val platformCount: Int,
    val token: String? = null
)
