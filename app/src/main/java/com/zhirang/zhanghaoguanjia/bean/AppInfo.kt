package com.zhirang.zhanghaoguanjia.bean

import android.graphics.drawable.Drawable


data class AppInfo(
    val name: String,
    val icon: Drawable?,
    val packageName: String,
    val sourceDir: String,
    val isXpModule: Boolean,
    val shopId: String? = null,
    val shopName: String? = null,
    val platform: String? = null,
    val platformAvailable: Boolean = true,
    val platformIconUrl: String? = null,
    val platformPackageName: String? = null
)
