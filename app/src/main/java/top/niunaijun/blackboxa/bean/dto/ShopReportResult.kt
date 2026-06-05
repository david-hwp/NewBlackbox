package top.niunaijun.blackboxa.bean.dto

data class ShopReportResult(
    val shopId: String,
    val deducted: Int,
    val balance: Int,
    val isNew: Boolean
)
