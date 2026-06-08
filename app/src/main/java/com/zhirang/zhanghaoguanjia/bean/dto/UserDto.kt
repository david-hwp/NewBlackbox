package com.zhirang.zhanghaoguanjia.bean.dto

data class UserDto(
    val id: Long,
    val username: String,
    val avatarUrl: String? = null,
    val phone: String,
    val role: String,
    val computeBalance: Int,
    val nonTransferableComputeBalance: Int = 0,
    val shopCount: Int,
    val platformCount: Int,
    val apkChannel: String? = DEFAULT_APK_CHANNEL,
    val token: String? = null
) {
    val normalizedApkChannel: String
        get() = apkChannel?.takeIf { it.isNotBlank() } ?: DEFAULT_APK_CHANNEL

    fun normalizedForStorage(): UserDto = copy(apkChannel = normalizedApkChannel)

    companion object {
        const val DEFAULT_APK_CHANNEL = "main"
    }
}
