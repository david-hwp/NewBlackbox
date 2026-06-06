package top.niunaijun.blackbox.engine

import android.content.Context
import androidx.multidex.MultiDexApplication
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

class EngineApp : MultiDexApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        val config = object : ClientConfiguration() {
            override fun getHostPackageName(): String {
                // Engine APK runs as a separate package, so host pkg is ourselves
                return packageName
            }

            override fun isEnableLauncherActivity(): Boolean {
                return false
            }
        }
        BlackBoxCore.get().doAttachBaseContext(this, config)
    }

    override fun onCreate() {
        super.onCreate()
        BlackBoxCore.get().doCreate()
        // Note: BlackBoxEngineService is started on-demand via bindService()
        // from the host app. Explicit startService() is not needed here
        // and would fail on Android 8.0+ when engine is in background.
        disableLauncherActivity()
    }

    /**
     * Explicitly disable LauncherActivity to prevent the engine APK
     * from showing an icon on the home screen.
     */
    private fun disableLauncherActivity() {
        try {
            val componentName = android.content.ComponentName(
                this,
                top.niunaijun.blackbox.app.LauncherActivity::class.java
            )
            packageManager.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            android.util.Log.w("EngineApp", "Failed to disable LauncherActivity: ${e.message}")
        }
    }
}
