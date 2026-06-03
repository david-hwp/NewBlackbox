package top.niunaijun.blackboxa.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * EngineInstallReceiver listens for Engine package installation/replacement events.
 * It notifies the splash screen or upgrade manager when the Engine is installed or updated.
 */
class EngineInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EngineInstallReceiver"

        const val ACTION_ENGINE_INSTALL_COMPLETE = "top.niunaijun.blackboxa.ACTION_ENGINE_INSTALL_COMPLETE"
        const val ACTION_ENGINE_UPGRADE_COMPLETE = "top.niunaijun.blackboxa.ACTION_ENGINE_UPGRADE_COMPLETE"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_SUCCESS = "success"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val packageName = intent.data?.schemeSpecificPart
                    if (packageName == EngineInstaller.ENGINE_PACKAGE) {
                        val isReplacing = intent.action == Intent.ACTION_PACKAGE_REPLACED
                        Log.i(TAG, "Engine package ${if (isReplacing) "replaced" else "added"}: $packageName")

                        // Notify internal components via local broadcast
                        val internalIntent = Intent(
                            if (isReplacing) ACTION_ENGINE_UPGRADE_COMPLETE
                            else ACTION_ENGINE_INSTALL_COMPLETE
                        ).apply {
                            setPackage(context.packageName)
                            putExtra(EXTRA_PACKAGE_NAME, packageName)
                            putExtra(EXTRA_SUCCESS, true)
                        }
                        context.sendBroadcast(internalIntent)
                    }
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    val packageName = intent.data?.schemeSpecificPart
                    if (packageName == EngineInstaller.ENGINE_PACKAGE) {
                        Log.w(TAG, "Engine package removed: $packageName")
                        // Engine was uninstalled - clear setup flag so it will be reinstalled
                        clearSetupFlag(context)
                    }
                }

                ACTION_ENGINE_INSTALL_COMPLETE,
                ACTION_ENGINE_UPGRADE_COMPLETE -> {
                    // Internal broadcasts - handle if needed
                    val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
                    val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                    Log.d(TAG, "Internal broadcast: ${intent.action}, success=$success, pkg=$pkg")
                }

                else -> {
                    // PackageInstaller status callback (no explicit action)
                    if (intent.hasExtra(PackageInstaller.EXTRA_STATUS)) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                        when (status) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                Log.d(TAG, "PackageInstaller: pending user action")
                                // Launch the system install confirmation dialog
                                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                                }
                                if (confirmIntent != null) {
                                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        context.startActivity(confirmIntent)
                                        Log.i(TAG, "Launched install confirmation dialog")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to launch confirmation intent: ${e.message}")
                                    }
                                } else {
                                    Log.w(TAG, "No confirmation intent provided for PENDING_USER_ACTION")
                                }
                            }
                            PackageInstaller.STATUS_SUCCESS -> {
                                Log.i(TAG, "PackageInstaller install success via status callback")
                                val internalIntent = Intent(ACTION_ENGINE_INSTALL_COMPLETE).apply {
                                    setPackage(context.packageName)
                                    putExtra(EXTRA_PACKAGE_NAME, EngineInstaller.ENGINE_PACKAGE)
                                    putExtra(EXTRA_SUCCESS, true)
                                }
                                context.sendBroadcast(internalIntent)
                            }
                            else -> {
                                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                Log.e(TAG, "PackageInstaller failed: status=$status, msg=$msg")
                            }
                        }
                    } else {
                        // Not a PackageInstaller callback — ignore
                        Log.d(TAG, "Ignoring broadcast with no actionable extras: ${intent.action}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling broadcast: ${e.message}", e)
        }
    }

    private fun clearSetupFlag(context: Context) {
        try {
            context.getSharedPreferences("engine_setup_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("setup_complete")
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing setup flag: ${e.message}")
        }
    }
}
