package com.zhirang.zhanghaoguanjia.engine

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log

/**
 * EngineVersionChecker checks for available Engine upgrades.
 * Currently implements a mock/placeholder that returns null (no upgrade).
 * The actual server API will be implemented in Phase 6 (Auth + Billing).
 */
object EngineVersionChecker {
    private const val TAG = "EngineVersionChecker"
    private const val PREFS_NAME = "engine_upgrade_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_LAST_UPGRADE_INFO = "last_upgrade_info"
    private const val KEY_SKIP_VERSION = "skip_version"
    private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    data class UpgradeInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val isForce: Boolean,
        val changelog: String,
        val minAppVersion: Int
    )

    /**
     * Check if an Engine upgrade is available.
     * Returns UpgradeInfo if an upgrade is available, null otherwise.
     *
     * Flow:
     * 1. Get local installed version
     * 2. Get builtin (bundled) version
     * 3. Call server API (placeholder)
     * 4. Compare versions and return result
     *
     * This is a placeholder implementation. The actual server API will be
     * implemented in Phase 6.
     */
    fun checkForUpgrade(context: Context): UpgradeInfo? {
        return try {
            // Rate limiting: max 1 check per 5 minutes
            val prefs = getPrefs(context)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
            val now = System.currentTimeMillis()
            if (now - lastCheck < CHECK_INTERVAL_MS) {
                Log.d(TAG, "Skipping upgrade check (rate limited)")
                return null
            }

            // Get versions
            val localVersion = EngineInstaller.getInstalledEngineVersion(context)
            val builtinVersion = EngineInstaller.getBuiltinEngineVersion(context)
            val appVersion = getAppVersionCode(context)

            Log.d(TAG, "Checking for upgrade: local=$localVersion, builtin=$builtinVersion, app=$appVersion")

            // TODO: Phase 6 - Implement actual server API call
            // val response = callServerApi(localVersion, appVersion)

            // Placeholder: no upgrade available
            // In Phase 6, this will:
            // 1. POST /api/engine/latest_version
            // 2. Compare response.version_code > max(local, builtin)
            // 3. Check force upgrade and min_app_version constraints
            // 4. Return UpgradeInfo or null

            val upgradeInfo = checkForUpgradePlaceholder(localVersion, builtinVersion, appVersion)

            // Update last check time
            prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

            if (upgradeInfo != null) {
                Log.i(TAG, "Upgrade available: ${upgradeInfo.versionName} (${upgradeInfo.versionCode})")
            } else {
                Log.d(TAG, "No upgrade available")
            }

            upgradeInfo
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for upgrade: ${e.message}", e)
            null
        }
    }

    /**
     * Check if a specific version has been skipped by the user.
     */
    fun isVersionSkipped(context: Context, versionCode: Int): Boolean {
        val skippedVersion = getPrefs(context).getInt(KEY_SKIP_VERSION, 0)
        return skippedVersion == versionCode
    }

    /**
     * Mark a version as skipped by the user.
     */
    fun skipVersion(context: Context, versionCode: Int) {
        getPrefs(context).edit().putInt(KEY_SKIP_VERSION, versionCode).apply()
        Log.d(TAG, "Version $versionCode skipped by user")
    }

    /**
     * Clear the skipped version flag.
     */
    fun clearSkippedVersion(context: Context) {
        getPrefs(context).edit().remove(KEY_SKIP_VERSION).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Placeholder implementation that always returns null (no upgrade).
     * Will be replaced with actual server API call in Phase 6.
     */
    /**
     * Get the app version code from PackageManager.
     */
    private fun getAppVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting app version code: ${e.message}")
            0
        }
    }

    private fun checkForUpgradePlaceholder(
        localVersion: Int,
        builtinVersion: Int,
        appVersion: Int
    ): UpgradeInfo? {
        // Built-in engine is newer than installed engine -> trigger local upgrade
        if (builtinVersion > localVersion && localVersion > 0) {
            Log.i(TAG, "Built-in engine ($builtinVersion) is newer than installed ($localVersion), triggering local upgrade")
            return UpgradeInfo(
                versionCode = builtinVersion,
                versionName = "$builtinVersion",
                downloadUrl = "", // Local upgrade uses bundled APK, no download needed
                isForce = true,   // Force upgrade to prevent data loss from manual uninstall
                changelog = "Engine update with latest features and fixes",
                minAppVersion = 0
            )
        }
        return null
    }
}
