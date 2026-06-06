package com.zhirang.zhanghaoguanjia.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EngineVersionChecker checks for available Engine upgrades.
 */
object EngineVersionChecker {
    private const val TAG = "EngineVersionChecker"
    private const val PREFS_NAME = "engine_upgrade_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_SKIP_VERSION = "skip_version"
    private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    data class UpgradeInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val checksum: String?,
        val isForce: Boolean,
        val changelog: String,
        val minAppVersion: Int
    )

    /**
     * Check if an Engine upgrade is available.
     * Returns UpgradeInfo if an upgrade is available, null otherwise.
     *
     * Checks local bundled upgrades first, then the server's highest available
     * engine version.
     */
    suspend fun checkForUpgrade(context: Context, force: Boolean = false): UpgradeInfo? {
        return try {
            val prefs = getPrefs(context)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
            val now = System.currentTimeMillis()
            if (!force && now - lastCheck < CHECK_INTERVAL_MS) {
                Log.d(TAG, "Skipping upgrade check (rate limited)")
                return null
            }

            val localVersion = EngineInstaller.getInstalledEngineVersion(context)
            val builtinVersion = EngineInstaller.getBuiltinEngineVersion(context)
            val appVersion = getAppVersionCode(context)
            val currentComparableVersion = maxOf(localVersion, builtinVersion)
            EngineUpgradeState.clearPendingIfInstalled(context, localVersion)

            Log.d(TAG, "Checking for upgrade: local=$localVersion, builtin=$builtinVersion, app=$appVersion")

            val upgradeInfo = checkForBuiltinUpgrade(localVersion, builtinVersion)
                ?: checkForServerUpgrade(currentComparableVersion)

            prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

            if (upgradeInfo != null) {
                if (upgradeInfo.downloadUrl.isNotBlank()) {
                    EngineUpgradeState.markPending(context, upgradeInfo.versionCode)
                }
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

    private fun checkForBuiltinUpgrade(
        localVersion: Int,
        builtinVersion: Int
    ): UpgradeInfo? {
        if (builtinVersion > localVersion && localVersion > 0) {
            Log.i(TAG, "Built-in engine ($builtinVersion) is newer than installed ($localVersion), triggering local upgrade")
            return UpgradeInfo(
                versionCode = builtinVersion,
                versionName = "$builtinVersion",
                downloadUrl = "", // Local upgrade uses bundled APK, no download needed
                checksum = null,
                isForce = true,   // Force upgrade to prevent data loss from manual uninstall
                changelog = "Engine update with latest features and fixes",
                minAppVersion = 0
            )
        }
        return null
    }

    private suspend fun checkForServerUpgrade(currentVersion: Int): UpgradeInfo? = withContext(Dispatchers.IO) {
        val response = RetrofitClient.apiService.getEngineVersions(true)
        if (response.code != 200) {
            Log.w(TAG, "Server upgrade check failed: ${response.code} ${response.message}")
            return@withContext null
        }
        val latest = response.data
            ?.filter { it.available }
            ?.maxByOrNull { it.versionCode }
            ?: return@withContext null
        if (latest.versionCode <= currentVersion) {
            return@withContext null
        }
        UpgradeInfo(
            versionCode = latest.versionCode,
            versionName = latest.versionName,
            downloadUrl = latest.apkUrl,
            checksum = latest.checksum,
            isForce = false,
            changelog = latest.changelog?.takeIf { it.isNotBlank() } ?: "发现新的引擎版本，请升级后继续使用最新能力",
            minAppVersion = 0
        )
    }
}
