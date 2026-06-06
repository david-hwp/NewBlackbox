package com.zhirang.zhanghaoguanjia.bean.dto

data class GiftResult(
    val fromUser: String,
    val toUser: String,
    val amount: Int,
    val fromBalance: Int,
    val toBalance: Int
)
