package top.niunaijun.blackboxa.bean

import android.graphics.drawable.Drawable


data class InstalledAppBean(
    val name: String,
    val icon: Drawable?,
    val packageName: String,
    val sourceDir: String,
    val isInstall: Boolean,
    val shopId: String? = null,
    val shopName: String? = null,
    val platform: String? = null
)
