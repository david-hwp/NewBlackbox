package com.zhirang.zhanghaoguanjia.bean.dto

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
    val confirmPassword: String
)
