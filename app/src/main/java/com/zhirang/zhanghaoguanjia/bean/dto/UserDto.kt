package com.zhirang.zhanghaoguanjia.bean.dto

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val subscriptionPlan: String? = SUBSCRIPTION_NONE,
    val subscriptionExpiresAt: String? = null,
    val subscriptionUpdatedAt: String? = null,
    val subscriptionActive: Boolean? = null,
    val token: String? = null
) {
    val normalizedApkChannel: String
        get() = apkChannel?.takeIf { it.isNotBlank() } ?: DEFAULT_APK_CHANNEL

    val hasSubscriptionRecord: Boolean
        get() = !subscriptionPlan.isNullOrBlank() &&
                !subscriptionPlan.equals(SUBSCRIPTION_NONE, ignoreCase = true)

    val isSubscriptionActiveNow: Boolean
        get() = subscriptionActive == true ||
                (parseSubscriptionExpiresAt()?.isAfter(LocalDateTime.now()) == true)

    val formattedSubscriptionExpiresAt: String?
        get() = parseSubscriptionExpiresAt()?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    fun normalizedForStorage(): UserDto = copy(apkChannel = normalizedApkChannel)

    private fun parseSubscriptionExpiresAt(): LocalDateTime? {
        val value = subscriptionExpiresAt?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            LocalDateTime.parse(value.substringBefore("+").substringBefore("Z"))
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_APK_CHANNEL = "main"
        const val SUBSCRIPTION_NONE = "NONE"
    }
}
