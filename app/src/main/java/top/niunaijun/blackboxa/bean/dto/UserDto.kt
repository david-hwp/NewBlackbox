package top.niunaijun.blackboxa.bean.dto

data class UserDto(
    val id: Long,
    val username: String,
    val phone: String,
    val role: String,
    val computeBalance: Int,
    val shopCount: Int,
    val platformCount: Int,
    val token: String? = null
)
