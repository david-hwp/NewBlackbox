package top.niunaijun.blackboxa.bean

data class UserProfile(
    val id: Long,
    val username: String,
    val phone: String,
    val avatarUrl: String? = null,
    val computeBalance: Int = 0,
    val shopCount: Int = 0,
    val platformCount: Int = 0
)
