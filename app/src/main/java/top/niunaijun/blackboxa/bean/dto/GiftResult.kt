package top.niunaijun.blackboxa.bean.dto

data class GiftResult(
    val fromUser: String,
    val toUser: String,
    val amount: Int,
    val fromBalance: Int,
    val toBalance: Int
)
