package com.zhirang.zhanghaoguanjia.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * EngineUpgradeManager handles online upgrades for the Engine APK.
 */
object EngineUpgradeManager {
    private const val TAG = "EngineUpgradeManager"
    private const val UPDATE_DIR = "engine-update"
    private const val BACKUP_DIR = "engine-backup"
    private const val BIND_VERIFY_TIMEOUT_MS = 30000L
    private const val BIND_VERIFY_INTERVAL_MS = 1000L
    private val httpClient = OkHttpClient()

    private var isUpgrading = false

    /**
     * Download and install an Engine upgrade.
     */
    suspend fun downloadAndInstall(
        context: Context,
        upgradeInfo: EngineVersionChecker.UpgradeInfo
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isUpgrading) {
                return@withContext Result.failure(IllegalStateException("Upgrade already in progress"))
            }
            isUpgrading = true

            Log.i(TAG, "Starting upgrade to ${upgradeInfo.versionName} (${upgradeInfo.versionCode})")

            if (!EngineInstaller.canInstallUnknownApps(context)) {
                return@withContext Result.failure(IllegalStateException("请先允许账号管家安装未知应用"))
            }

            val downloadedFile = downloadEngineApk(context, upgradeInfo)
            if (!verifyDownloadedFile(downloadedFile, upgradeInfo)) {
                return@withContext Result.failure(IllegalStateException("引擎包校验失败"))
            }
            if (!verifyMinSdk(context, downloadedFile)) {
                return@withContext Result.failure(IllegalStateException("当前系统版本不支持该引擎包"))
            }
            val validation = EngineInstaller.validateInstallCandidate(context, downloadedFile)
            if (validation.isFailure) {
                return@withContext Result.failure(
                    validation.exceptionOrNull() ?: IllegalStateException("引擎包不可安装")
                )
            }
            installNewEngine(context, downloadedFile)
        } catch (e: Exception) {
            Log.e(TAG, "Upgrade failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            isUpgrading = false
        }
    }

    /**
     * Check if an upgrade is currently in progress.
     */
    fun isUpgrading(): Boolean = isUpgrading

    // === Internal methods (to be fully implemented in Phase 6) ===

    /**
     * Download the Engine APK to the update directory.
     */
    private suspend fun downloadEngineApk(
        context: Context,
        upgradeInfo: EngineVersionChecker.UpgradeInfo
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val destFile = File(updateDir, "engine-${upgradeInfo.versionCode}.apk")

        val resolvedUrl = RetrofitClient.resolveUrl(upgradeInfo.downloadUrl)
        Log.d(TAG, "Downloading from $resolvedUrl to ${destFile.absolutePath}")
        val response = httpClient.newCall(Request.Builder().url(resolvedUrl).build()).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("下载失败: ${response.code}")
        }
        val body = response.body ?: throw IllegalStateException("下载内容为空")
        if (destFile.exists()) {
            destFile.delete()
        }
        body.byteStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile
    }

    /**
     * Verify downloaded file integrity using MD5/SHA256.
     */
    private fun verifyDownloadedFile(
        file: File,
        upgradeInfo: EngineVersionChecker.UpgradeInfo
    ): Boolean {
        val expectedMd5 = upgradeInfo.checksum?.takeIf { it.isNotBlank() } ?: return true
        val actualMd5 = EngineInstaller.computeFileMd5(file) ?: return false
        val matched = actualMd5.equals(expectedMd5, ignoreCase = true)
        if (!matched) {
            Log.e(TAG, "Engine MD5 mismatch: expected=$expectedMd5 actual=$actualMd5")
        }
        return matched
    }

    /**
     * Verify APK signature fingerprint matches trusted whitelist.
     */
    private fun verifyApkSignature(context: Context, file: File): Boolean {
        // TODO: Phase 6 - Extract signature and compare against whitelist
        Log.d(TAG, "Verifying APK signature: ${file.absolutePath}")
        return true
    }

    /**
     * Verify the APK's minSdkVersion is compatible with the device.
     */
    private fun verifyMinSdk(context: Context, file: File): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                packageInfo?.applicationInfo?.minSdkVersion ?: 0
            } else {
                0
            }
            val deviceSdk = Build.VERSION.SDK_INT
            val compatible = minSdk <= deviceSdk
            if (!compatible) {
                Log.e(TAG, "Incompatible minSdk: APK requires $minSdk, device has $deviceSdk")
            }
            compatible
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying minSdk: ${e.message}")
            false
        }
    }

    /**
     * Backup the currently installed Engine APK.
     */
    private fun backupCurrentEngine(context: Context): File? {
        return try {
            val backupDir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
            val installedPath = EngineLoader.getEngineApkPath(context)
            if (installedPath == null) {
                Log.w(TAG, "No installed Engine to backup")
                return null
            }

            val installedFile = File(installedPath)
            val version = EngineInstaller.getInstalledEngineVersion(context)
            val backupFile = File(backupDir, "engine-backup-$version.apk")

            installedFile.inputStream().use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Engine backed up to: ${backupFile.absolutePath}")
            backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up Engine: ${e.message}", e)
            null
        }
    }

    /**
     * Install the new Engine APK.
     */
    private fun installNewEngine(context: Context, apkFile: File): Result<Unit> {
        return EngineInstaller.installFromFile(context, apkFile)
    }

    /**
     * Verify the new Engine responds to bindService.
     */
    private suspend fun verifyNewEngineBind(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            var attempts = 0
            val maxAttempts = (BIND_VERIFY_TIMEOUT_MS / BIND_VERIFY_INTERVAL_MS).toInt()

            while (attempts < maxAttempts) {
                if (EngineLoader.isEngineInstalled(context)) {
                    // Try to bind
                    val connection = EngineConnection()
                    val bound = connection.bind(context)
                    if (bound) {
                        delay(2000) // Wait for service connection
                        if (EngineConnection.isConnected()) {
                            connection.unbind(context)
                            Log.i(TAG, "New Engine bind verified successfully")
                            return@withContext true
                        }
                        connection.unbind(context)
                    }
                }
                delay(BIND_VERIFY_INTERVAL_MS)
                attempts++
            }

            Log.e(TAG, "New Engine bind verification failed after ${BIND_VERIFY_TIMEOUT_MS}ms")
            false
        }
    }

    /**
     * Rollback to the backup Engine APK.
     */
    private suspend fun rollback(context: Context, backupFile: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "Rolling back to backup: ${backupFile.absolutePath}")

                if (!backupFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Backup file not found"))
                }

                // Reinstall backup APK
                val result = EngineInstaller.installFromAssets(context)
                // TODO: Phase 6 - Use PackageInstaller for backup file

                Log.i(TAG, "Rollback completed")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Rollback failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Cleanup downloaded and backup files after upgrade.
     */
    private fun cleanup(
        context: Context,
        downloadedFile: File?,
        backupFile: File?,
        success: Boolean
    ) {
        try {
            // Delete downloaded APK after successful install
            downloadedFile?.let {
                if (it.exists()) {
                    it.delete()
                    Log.d(TAG, "Deleted downloaded APK: ${it.absolutePath}")
                }
            }

            // Delete old backup after new version confirmed working
            if (success) {
                backupFile?.let {
                    if (it.exists()) {
                        it.delete()
                        Log.d(TAG, "Deleted backup APK: ${it.absolutePath}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup: ${e.message}")
        }
    }

    /**
     * Get the list of available backup files.
     */
    fun getBackupFiles(context: Context): List<File> {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        return if (backupDir.exists()) {
            backupDir.listFiles { file -> file.name.endsWith(".apk") }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Delete all backup files.
     */
    fun clearBackups(context: Context) {
        try {
            val backupDir = File(context.filesDir, BACKUP_DIR)
            backupDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "All backups cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing backups: ${e.message}")
        }
    }
}
