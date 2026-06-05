package top.niunaijun.blackboxa.bean.dto

data class ShopValidationResult(
    val shopId: String,
    val isValid: Boolean,
    val expireAt: String,
    val autoRenew: Boolean
)
