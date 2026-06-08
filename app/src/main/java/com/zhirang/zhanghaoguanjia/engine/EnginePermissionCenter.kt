package com.zhirang.zhanghaoguanjia.engine

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object EnginePermissionCenter {
    private const val TAG = "EnginePermissionCenter"
    private const val PREF_NAME = "engine_permission_center"
    private const val KEY_BASELINE_PROMPTED_ENGINE_VERSION = "baseline_prompted_engine_version"
    private const val ENGINE_PERMISSION_ACTIVITY =
        "top.niunaijun.blackbox.engine.EnginePermissionActivity"

    const val EXTRA_PERMISSIONS = "permissions"
    const val EXTRA_TITLE = "title"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_REQUEST_REASON = "request_reason"

    private const val REQUEST_REASON_BASELINE = "baseline"
    private const val REQUEST_REASON_PLATFORM = "platform"
    private const val DOUYIN_LAIKE_PACKAGE = "com.bytedance.ls.merchant"

    private val runtimePermissionCandidates = setOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
    )

    fun baselinePermissions(): List<String> {
        val permissions = linkedSetOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
            permissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        return permissions.filter(::isPermissionSupportedBySdk)
    }

    fun platformRequiredPermissions(context: Context, packageName: String?): List<String> {
        if (packageName.isNullOrBlank()) {
            return emptyList()
        }
        val permissions = linkedSetOf<String>()
        permissions += readManifestPermissions(context, packageName)
            .filter { it in runtimePermissionCandidates }
            .filter(::isPermissionSupportedBySdk)
        if (packageName == DOUYIN_LAIKE_PACKAGE) {
            permissions += Manifest.permission.CAMERA
            permissions += Manifest.permission.RECORD_AUDIO
        }
        return permissions.toList()
    }

    fun missingEnginePermissions(context: Context, permissions: Collection<String>): List<String> {
        return permissions
            .asSequence()
            .filter(::isPermissionSupportedBySdk)
            .distinct()
            .filter { !isEnginePermissionGranted(context, it) || !isEngineAppOpAllowed(context, it) }
            .toList()
    }

    fun hasEnginePermissions(context: Context, permissions: Collection<String>): Boolean {
        return missingEnginePermissions(context, permissions).isEmpty()
    }

    fun shouldPromptBaseline(context: Context): Boolean {
        val engineVersion = EngineInstaller.getInstalledEngineVersion(context)
        if (engineVersion <= 0) {
            return false
        }
        val missing = missingEnginePermissions(context, baselinePermissions())
        if (missing.isEmpty()) {
            markBaselinePrompted(context)
            return false
        }
        return prefs(context).getInt(KEY_BASELINE_PROMPTED_ENGINE_VERSION, -1) != engineVersion
    }

    fun markBaselinePrompted(context: Context) {
        val engineVersion = EngineInstaller.getInstalledEngineVersion(context)
        if (engineVersion <= 0) {
            return
        }
        prefs(context)
            .edit()
            .putInt(KEY_BASELINE_PROMPTED_ENGINE_VERSION, engineVersion)
            .apply()
    }

    fun buildBaselineIntent(context: Context): Intent {
        return buildPermissionIntent(
            permissions = missingEnginePermissions(context, baselinePermissions()),
            title = "引擎权限",
            message = "请允许店铺管家引擎使用基础权限，以保证店铺登录、扫码、人脸识别和消息能力正常使用。",
            reason = REQUEST_REASON_BASELINE
        )
    }

    fun buildPlatformIntent(context: Context, packageName: String?): Intent {
        val missing = missingEnginePermissions(context, platformRequiredPermissions(context, packageName))
        return buildPermissionIntent(
            permissions = missing,
            title = "店铺权限",
            message = "请允许店铺管家引擎使用该平台需要的权限，否则店铺可能无法正常打开或完成认证。",
            reason = REQUEST_REASON_PLATFORM
        )
    }

    private fun buildPermissionIntent(
        permissions: Collection<String>,
        title: String,
        message: String,
        reason: String
    ): Intent {
        return Intent().apply {
            component = ComponentName(EngineInstaller.ENGINE_PACKAGE, ENGINE_PERMISSION_ACTIVITY)
            putExtra(EXTRA_PERMISSIONS, permissions.distinct().toTypedArray())
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_REQUEST_REASON, reason)
        }
    }

    private fun readManifestPermissions(context: Context, packageName: String): List<String> {
        return try {
            val flags = PackageManager.GET_PERMISSIONS
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(packageName, flags)
            packageInfo.requestedPermissions?.toList().orEmpty()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Target package is not visible to host package manager: $packageName")
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read permissions from $packageName", e)
            emptyList()
        }
    }

    private fun isEnginePermissionGranted(context: Context, permission: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return try {
            context.packageManager.checkPermission(
                permission,
                EngineInstaller.ENGINE_PACKAGE
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check engine permission $permission", e)
            false
        }
    }

    private fun isEngineAppOpAllowed(context: Context, permission: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val op = appOpForPermission(permission) ?: return true
        return try {
            val appInfo = context.packageManager.getApplicationInfo(EngineInstaller.ENGINE_PACKAGE, 0)
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, appInfo.uid, EngineInstaller.ENGINE_PACKAGE)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, appInfo.uid, EngineInstaller.ENGINE_PACKAGE)
            }
            mode == AppOpsManager.MODE_ALLOWED ||
                    mode == AppOpsManager.MODE_DEFAULT ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            mode == AppOpsManager.MODE_FOREGROUND)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check engine app op for $permission", e)
            false
        }
    }

    private fun appOpForPermission(permission: String): String? {
        return when (permission) {
            Manifest.permission.CAMERA -> AppOpsManager.OPSTR_CAMERA
            Manifest.permission.RECORD_AUDIO -> AppOpsManager.OPSTR_RECORD_AUDIO
            Manifest.permission.ACCESS_FINE_LOCATION -> AppOpsManager.OPSTR_FINE_LOCATION
            Manifest.permission.ACCESS_COARSE_LOCATION -> AppOpsManager.OPSTR_COARSE_LOCATION
            Manifest.permission.READ_EXTERNAL_STORAGE -> AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE
            else -> null
        }
    }

    private fun isPermissionSupportedBySdk(permission: String): Boolean {
        return when (permission) {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
            else -> true
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
