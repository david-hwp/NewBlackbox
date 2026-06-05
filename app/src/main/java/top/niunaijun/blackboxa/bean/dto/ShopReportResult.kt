package top.niunaijun.blackboxa.bean.dto

data class ShopReportResult(
    val shopId: String,
    val deducted: Boolean,
    val balance: Int,
    val isNew: Boolean,
    val shopCount: Int? = null,
    val platformCount: Int? = null,
    val cloneInstanceId: String? = null
)
