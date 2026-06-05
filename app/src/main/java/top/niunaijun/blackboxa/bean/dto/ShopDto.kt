package top.niunaijun.blackboxa.bean.dto

data class ShopDto(
    val id: Long,
    val shopName: String,
    val shopId: String,
    val platform: String,
    val platformName: String,
    val remainingDays: Int,
    val autoRenew: Boolean,
    val packageName: String?
)
