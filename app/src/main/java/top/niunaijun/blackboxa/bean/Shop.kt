package top.niunaijun.blackboxa.bean

data class Shop(
    val id: Long,
    val shopName: String,
    val shopId: String,
    val platform: Platform,
    val remainingDays: Int = 0,
    val autoRenew: Boolean = false,
    val packageName: String? = null,
    val icon: Any? = null,  // 占位，后续接入真实图标
    val isNew: Boolean = false
)
