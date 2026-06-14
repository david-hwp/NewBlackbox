package com.zhirang.zhanghaoguanjia.bean

data class LogEntry(
    val id: Long,
    val type: LogType,
    val amount: Int,
    val description: String,
    val timestamp: Long
)

enum class LogType { CONSUME, OUT, IN, PHONE_CONSUME, PHONE_OUT, PHONE_IN }
