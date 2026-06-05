package com.zhirang.zhanghaoguanjia.bean.dto

data class UpdateUsernameRequest(
    val username: String,
    val avatarUrl: String? = null
)
