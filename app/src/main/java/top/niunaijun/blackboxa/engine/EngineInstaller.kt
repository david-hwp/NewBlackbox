package top.niunaijun.blackboxa.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * EngineInstaller handles first-time installation of the Engine APK from assets.
 * It also provides version checking utilities for the installed and bundled Engine.
 */
object EngineInstaller {
    private const val TAG = "EngineInstaller"
    const val ENGINE_PACKAGE = "top.niunaijun.blackbox.engine"
    private const val ENGINE_ASSET_NAME = "engine/engine-base.apk"
    private const val ENGINE_FILE_NAME = "engine-base.apk"
    private const val ENGINE_DIR = "engine"
    const val INSTALL_TIMEOUT_MS = 60000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Check if the Engine APK is installed as a separate app on the device.
     */
    fun isEngineInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ENGINE_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking engine installation: ${e.message}")
            false
        }
    }

    /**
     * Get the versionCode of the installed Engine, or 0 if not installed.
     */
    fun getInstalledEngineVersion(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(ENGINE_PACKAGE, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0
        } catch (e: Exception) {
            Log.w(TAG, "Error getting installed engine version: ${e.message}")
            0
        }
    }

    /**
     * Get the versionCode of the bundled Engine APK in assets, or 0 if not found.
     */
    fun getBuiltinEngineVersion(context: Context): Int {
        return try {
            val assetPath = copyAssetToTemp(context) ?: return 0
            val packageInfo = context.packageManager.getPackageArchiveInfo(assetPath, 0)
            val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode?.toInt() ?: 0
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode ?: 0
            }
            // Clean up temp file
            try {
                File(assetPath).delete()
            } catch (_: Exception) {
            }
            version
        } catch (e: Exception) {
            Log.w(TAG, "Error getting builtin engine version: ${e.message}")
            0
        }
    }

    /**
     * Install the Engine APK from assets.
     * Returns Result.success when install intent is sent successfully.
     * Actual install completion is tracked via PackageInstaller callback or broadcast.
     */
    fun installFromAssets(context: Context): Result<Unit> {
        return try {
            // Step 1: Copy from assets to private files dir
            val engineDir = File(context.filesDir, ENGINE_DIR).apply { mkdirs() }
            val destFile = File(engineDir, ENGINE_FILE_NAME)

            // Copy to temp first for atomicity
            val tempFile = File(engineDir, "$ENGINE_FILE_NAME.tmp")

            context.assets.open(ENGINE_ASSET_NAME).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Verify the copied file is a valid APK
            val packageInfo = context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
            if (packageInfo == null) {
                tempFile.delete()
                return Result.failure(IOException("Copied file is not a valid APK"))
            }

            // Verify package name matches expected
            if (packageInfo.packageName != ENGINE_PACKAGE) {
                tempFile.delete()
                return Result.failure(
                    SecurityException(
                        "APK package name mismatch: expected $ENGINE_PACKAGE, got ${packageInfo.packageName}"
                    )
                )
            }

            // Atomic move: temp -> dest
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.delete()
                return Result.failure(IOException("Failed to move temp file to destination"))
            }

            Log.d(TAG, "Engine APK copied to: ${destFile.absolutePath}")

            // Step 2: Trigger installation
            installApk(context, destFile)
        } catch (e: IOException) {
            Log.e(TAG, "IO error installing from assets: ${e.message}", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error installing from assets: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error installing from assets: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get the path to the Engine APK in private files dir (after copyFromAssets).
     */
    fun getEngineApkPath(context: Context): String? {
        val file = File(context.filesDir, "$ENGINE_DIR/$ENGINE_FILE_NAME")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Check if the app has permission to install unknown apps.
     */
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Open system settings to grant "install unknown apps" permission.
     */
    fun openInstallPermissionSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening install permission settings: ${e.message}")
        }
    }

    /**
     * Install an APK file using the appropriate method for the device/Android version.
     *
     * MIUI devices use ACTION_VIEW intent because MIUI's custom package installer
     * (com.miui.packageinstaller) auto-aborts PackageInstaller API installs with
     * "User rejected permissions" before the user can interact with the dialog.
     */
    private fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (isMiui()) {
                Log.d(TAG, "Using intent-based install for MIUI")
                installWithIntent(context, apkFile)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                installWithPackageInstaller(context, apkFile)
            } else {
                installWithIntent(context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Detect if running on MIUI (Xiaomi/Redmi/Poco).
     */
    private fun isMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        return manufacturer.equals("Xiaomi", ignoreCase = true) ||
                manufacturer.equals("Redmi", ignoreCase = true) ||
                brand.equals("Xiaomi", ignoreCase = true) ||
                brand.equals("Redmi", ignoreCase = true)
    }

    /**
     * Install using PackageInstaller API (Android 5.0+).
     */
    private fun installWithPackageInstaller(context: Context, apkFile: File): Result<Unit> {
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            // Set APK size so the system can pre-allocate storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                params.setSize(apkFile.length())
            }

            val sessionId = packageInstaller.createSession(params)
            Log.d(TAG, "PackageInstaller session created: $sessionId")

            val session = packageInstaller.openSession(sessionId)

            session.openWrite("package", 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    val copied = input.copyTo(output)
                    Log.d(TAG, "PackageInstaller wrote $copied bytes to session $sessionId")
                }
                // Skip fsync on MIUI — it can block for seconds on slow storage
                // session.fsync(output)
            }

            val intent = Intent(context, EngineInstallReceiver::class.java)
            // PackageInstaller status callback requires MUTABLE on Android 12+ so the system
            // can fill in EXTRA_STATUS / EXTRA_INTENT. IMMUTABLE blocks this.
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                piFlags
            )

            session.commit(pendingIntent.intentSender)
            // DO NOT call session.close() after commit() — the system will finish
            // the session asynchronously. Closing immediately cancels the install.

            Log.d(TAG, "PackageInstaller session committed: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller failed: ${e.message}", e)
            // Fallback to intent-based install
            installWithIntent(context, apkFile)
        }
    }

    /**
     * Install using ACTION_VIEW intent (fallback for older devices).
     */
    private fun installWithIntent(context: Context, apkFile: File): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            context.startActivity(intent)

            Log.d(TAG, "Intent-based install started")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Intent install failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Copy asset to a temporary file and return its path.
     */
    private fun copyAssetToTemp(context: Context): String? {
        return try {
            val tempFile = File(context.cacheDir, "engine-temp.apk")
            context.assets.open(ENGINE_ASSET_NAME).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Error copying asset to temp: ${e.message}")
            null
        }
    }

    /**
     * Compute MD5 hash of a file for verification.
     */
    fun computeFileMd5(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error computing MD5: ${e.message}")
            null
        }
    }
}
