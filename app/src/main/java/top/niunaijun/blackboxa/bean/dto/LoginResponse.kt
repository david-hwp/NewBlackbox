package top.niunaijun.blackboxa.bean.dto

data class LoginResponse(
    val user: UserDto,
    val token: String
)
