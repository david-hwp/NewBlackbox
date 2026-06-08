package top.niunaijun.blackbox.engine

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import top.niunaijun.blackbox.utils.Slog

class EnginePermissionActivity : Activity() {
    private var requestedRuntimePermissions = false
    private var openedSettings = false
    private var requiredPermissions: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requiredPermissions = resolveRequestedPermissions()
        requestOrFinish()
    }

    override fun onResume() {
        super.onResume()
        if (openedSettings) {
            openedSettings = false
            requestOrFinish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ENGINE_PERMISSIONS) {
            requestOrFinish()
        }
    }

    private fun requestOrFinish() {
        if (requiredPermissions.isEmpty() ||
            (hasRequiredPermissions() && areRequiredAppOpsAllowed())
        ) {
            setResult(RESULT_OK)
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val missingPermissions = missingRuntimePermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            missingPermissions.isNotEmpty() &&
            !requestedRuntimePermissions
        ) {
            requestedRuntimePermissions = true
            requestPermissions(missingPermissions.toTypedArray(), REQUEST_ENGINE_PERMISSIONS)
            return
        }

        showPermissionSettingsDialog()
    }

    private fun hasRequiredPermissions(): Boolean {
        return missingRuntimePermissions().isEmpty()
    }

    private fun missingRuntimePermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return emptyList()
        }
        return requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun areRequiredAppOpsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return requiredPermissions
            .mapNotNull(::appOpForPermission)
            .all(::isAppOpAllowed)
    }

    private fun isAppOpAllowed(op: String): Boolean {
        return try {
            val appOps = getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(op, applicationInfo.uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(op, applicationInfo.uid, packageName)
            }
            isAllowedAppOpMode(mode)
        } catch (e: Throwable) {
            Slog.w(TAG, "Failed to check app op $op: ${e.message}")
            true
        }
    }

    private fun isAllowedAppOpMode(mode: Int): Boolean {
        return mode == AppOpsManager.MODE_ALLOWED ||
                mode == AppOpsManager.MODE_DEFAULT ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        mode == AppOpsManager.MODE_FOREGROUND)
    }

    private fun showPermissionSettingsDialog() {
        if (isFinishing || isDestroyed) {
            return
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).takeUnless { it.isNullOrBlank() } ?: "需要授权"
        val message = intent?.getStringExtra(EXTRA_MESSAGE).takeUnless { it.isNullOrBlank() }
            ?: "请允许店铺管家引擎使用必要权限，否则部分店铺功能可能无法正常使用。"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消") { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
                overridePendingTransition(0, 0)
            }
            .setPositiveButton("去设置") { _, _ ->
                openedSettings = true
                openPermissionSettings()
            }
            .show()
    }

    private fun openPermissionSettings() {
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", packageName)
        }
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(miuiIntent)
        } catch (e: Throwable) {
            Slog.w(TAG, "Failed to open MIUI permission editor: ${e.message}")
            startActivity(fallbackIntent)
        }
    }

    private fun resolveRequestedPermissions(): List<String> {
        val extras = intent?.getStringArrayExtra(EXTRA_PERMISSIONS)
        val candidates = extras
            ?.filterNot { it.isNullOrBlank() }
            ?: DEFAULT_PERMISSIONS.toList()
        return candidates
            .asSequence()
            .filter(::isPermissionSupportedBySdk)
            .filter(::isDeclaredPermission)
            .distinct()
            .toList()
    }

    private fun isDeclaredPermission(permission: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.contains(permission) == true
        } catch (e: Throwable) {
            Slog.w(TAG, "Failed to read declared permissions: ${e.message}")
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

    companion object {
        private const val TAG = "EnginePermissionActivity"
        private const val REQUEST_ENGINE_PERMISSIONS = 7101
        private const val EXTRA_PERMISSIONS = "permissions"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private val DEFAULT_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
