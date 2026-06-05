package top.niunaijun.blackboxa.bean.dto

data class ShopReportResult(
    val shopId: String,
    val deducted: Boolean,
    val balance: Int,
    val isNew: Boolean,
    val cloneInstanceId: String? = null
)
