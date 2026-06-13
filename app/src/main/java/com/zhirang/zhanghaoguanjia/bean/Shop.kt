package com.zhirang.zhanghaoguanjia.bean

data class Shop(
    val id: Long,
    val shopName: String,
    val shopId: String,
    val platform: Platform,
    val remainingDays: Int = 0,
    val autoRenew: Boolean = false,
    val packageName: String? = null,
    val cloneInstanceId: String? = null,
    val localVirtualUserId: Int? = null,
    val hasLoginState: Boolean = false,
    val loginStateProfile: String? = null,
    val loginStateSize: Long? = null,
    val loginStateSha256: String? = null,
    val identityVerified: Boolean = false,
    val localIdentityVerified: Boolean? = null,
    val icon: Any? = null,  // 占位，后续接入真实图标
    val isNew: Boolean = false
) {
    val hasVerifiedIdentity: Boolean
        get() = (localIdentityVerified ?: identityVerified)
                && shopId.isNotBlank()
                && shopId != "-"
                && !shopId.startsWith("NEW-")
                && !shopId.startsWith("phase13-")
                && shopName.isNotBlank()
                && !shopName.startsWith("新增店铺-[")
                && !shopName.startsWith("NEW-")
                && !shopName.startsWith("phase13-")
                && !shopName.startsWith("User[")
                && !shopName.startsWith("未知")
}
