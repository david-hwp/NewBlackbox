package top.niunaijun.blackboxa.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.niunaijun.blackboxa.engine.EngineConnection
import top.niunaijun.blackboxa.engine.EngineLoader
import top.niunaijun.blackboxa.engine.EngineVersionChecker


class App : Application() {

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private lateinit var mContext: Context

        @JvmStatic
        fun getContext(): Context {
            return mContext
        }
    }

    private val engineConnection = EngineConnection()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun attachBaseContext(base: Context?) {
        try {
            super.attachBaseContext(base)

            mContext = base!!

            try {
                AppManager.doAttachBaseContext(base)
            } catch (e: Exception) {
                Log.e("App", "Error in doAttachBaseContext: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("App", "Critical error in attachBaseContext: ${e.message}")
            if (base != null) {
                mContext = base
            }
        }
    }

    override fun onCreate() {
        try {
            super.onCreate()
            AppManager.doOnCreate(mContext)

            // Initialize Engine connection after AppManager setup
            initEngineConnection()
        } catch (e: Exception) {
            Log.e("App", "Error in onCreate: ${e.message}")
        }
    }

    private fun initEngineConnection() {
        try {
            // Check if Engine APK is installed
            if (!EngineLoader.isEngineInstalled(mContext)) {
                Log.w("App", "Engine APK not installed. Some features will be unavailable.")
                return
            }

            // Initialize EngineLoader (extract/load native libs)
            val loaderSuccess = EngineLoader.init(mContext)
            if (!loaderSuccess) {
                Log.w("App", "EngineLoader init returned false")
            }

            // Bind to Engine Service
            val bound = engineConnection.bind(mContext)
            if (!bound) {
                Log.w("App", "Failed to bind to Engine Service")
            } else {
                Log.d("App", "Engine Service bind requested successfully")
            }

            // Check for Engine upgrades in background after successful bind
            checkForEngineUpgrade()
        } catch (e: Exception) {
            Log.e("App", "Error initializing Engine connection: ${e.message}")
        }
    }

    /**
     * Check for Engine upgrades in the background.
     * This runs silently and does not block app startup.
     */
    private fun checkForEngineUpgrade() {
        appScope.launch {
            try {
                Log.d("App", "Checking for Engine upgrades...")
                val upgradeInfo = EngineVersionChecker.checkForUpgrade(mContext)
                if (upgradeInfo != null) {
                    Log.i("App", "Engine upgrade available: ${upgradeInfo.versionName} (${upgradeInfo.versionCode})")
                    // Upgrade dialog will be shown by MainActivity observing this state
                    // For now, just log it. MainActivity will check again when resumed.
                } else {
                    Log.d("App", "No Engine upgrade available")
                }
            } catch (e: Exception) {
                Log.e("App", "Error checking for Engine upgrade: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        try {
            engineConnection.unbind(mContext)
        } catch (e: Exception) {
            Log.w("App", "Error unbinding Engine Service: ${e.message}")
        }
        super.onTerminate()
    }
}