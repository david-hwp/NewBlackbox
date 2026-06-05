package top.niunaijun.blackboxa.network

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class PagedResult<T>(
    val total: Int,
    val page: Int,
    val size: Int,
    val list: List<T>
)

class ApiException(val code: Int, message: String) : Exception(message)
