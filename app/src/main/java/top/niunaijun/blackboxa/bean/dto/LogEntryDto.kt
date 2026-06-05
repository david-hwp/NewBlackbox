package top.niunaijun.blackboxa.bean.dto

data class LogEntryDto(
    val id: Long,
    val type: String,
    val typeText: String? = null,
    val amount: Int,
    val platform: String?,
    val shopName: String?,
    val fromPhone: String?,
    val toPhone: String?,
    val createdAt: String
)
