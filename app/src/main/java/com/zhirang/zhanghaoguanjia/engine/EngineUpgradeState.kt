package com.zhirang.zhanghaoguanjia.engine

import android.content.Context

object EngineUpgradeState {
    private const val PREFS_NAME = "engine_upgrade_state"
    private const val KEY_PENDING_VERSION_CODE = "pending_version_code"

    fun markPending(context: Context, versionCode: Int) {
        if (versionCode <= 0) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PENDING_VERSION_CODE, versionCode)
            .apply()
    }

    fun clearPendingIfInstalled(context: Context, currentVersionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingVersionCode = prefs.getInt(KEY_PENDING_VERSION_CODE, 0)
        if (pendingVersionCode > 0 && currentVersionCode >= pendingVersionCode) {
            prefs.edit().remove(KEY_PENDING_VERSION_CODE).apply()
        }
    }

    fun hasPendingUpgrade(context: Context, currentVersionCode: Int): Boolean {
        val pendingVersionCode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PENDING_VERSION_CODE, 0)
        return pendingVersionCode > currentVersionCode
    }
}
