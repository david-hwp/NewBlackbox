package top.niunaijun.blackboxa.bean.dto

data class ShopReportRequest(
    val shopName: String,
    val shopId: String,
    val platform: String,
    val platformName: String,
    val packageName: String,
    val remainingDays: Int = 30,
    val autoRenew: Boolean = false
)
