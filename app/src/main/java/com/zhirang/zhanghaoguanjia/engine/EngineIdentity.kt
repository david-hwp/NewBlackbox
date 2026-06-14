package com.zhirang.zhanghaoguanjia.engine

import android.content.ComponentName
import com.zhirang.zhanghaoguanjia.BuildConfig

object EngineIdentity {
    const val ENGINE_SERVICE_CLASS = "top.niunaijun.blackbox.engine.BlackBoxEngineService"
    const val ENGINE_WAKE_ACTIVITY_CLASS = "top.niunaijun.blackbox.engine.EngineWakeActivity"
    const val ENGINE_PERMISSION_ACTIVITY_CLASS = "top.niunaijun.blackbox.engine.EnginePermissionActivity"

    val packageName: String
        get() = BuildConfig.ENGINE_PACKAGE.trim()

    val bindPermission: String
        get() = "$packageName.permission.BIND_ENGINE"

    fun component(className: String): ComponentName {
        return ComponentName(packageName, className)
    }
}
