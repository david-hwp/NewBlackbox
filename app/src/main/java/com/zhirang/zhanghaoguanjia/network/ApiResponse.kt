package com.zhirang.zhanghaoguanjia.network

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class PagedResult<T>(
    val total: Int = 0,
    val totalElements: Int = total,
    val page: Int = 1,
    val size: Int = 20,
    val list: List<T>? = null,
    val content: List<T>? = null
) {
    fun items(): List<T> = list?.takeIf { it.isNotEmpty() } ?: content.orEmpty()
    fun totalCount(): Int = if (total > 0) total else totalElements
}

class ApiException(val code: Int, message: String) : Exception(message)
