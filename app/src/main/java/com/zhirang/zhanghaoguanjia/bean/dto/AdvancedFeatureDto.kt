package com.zhirang.zhanghaoguanjia.bean.dto

data class AdvancedFeatureDto(
    val id: Long? = null,
    val code: String = "",
    val name: String = "",
    val online: Boolean = false,
    val monthlyComputeCost: Int = 0,
    val supportedPlatformPackages: List<String> = emptyList(),
    val title: String = "",
    val line1: String = "-",
    val line2: String = "-",
    val titleCode: String = "",
    val line1Code: String = "",
    val line2Code: String = "",
    val outboundEnabled: Boolean = false,
    val sortOrder: Int = 0
) {
    fun supportsPackage(packageName: String?): Boolean {
        val normalized = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val supported = supportedPlatformPackages
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        return supported.isEmpty() || supported.contains(normalized)
    }
}
