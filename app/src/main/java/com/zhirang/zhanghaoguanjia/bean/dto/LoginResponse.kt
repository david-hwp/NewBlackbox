package com.zhirang.zhanghaoguanjia.bean.dto

data class LoginResponse(
    val user: UserDto,
    val token: String
)
