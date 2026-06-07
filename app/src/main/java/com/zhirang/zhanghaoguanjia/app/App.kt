package com.zhirang.zhanghaoguanjia.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import com.zhirang.zhanghaoguanjia.engine.EngineConnection
import com.zhirang.zhanghaoguanjia.engine.EngineLoader


class App : Application() {

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private lateinit var mContext: Context

        @Volatile
        private lateinit var sApp: App

        @JvmStatic
        fun getContext(): Context {
            return mContext
        }

        @JvmStatic
        fun ensureEngineConnection(): Boolean {
            return if (::sApp.isInitialized) {
                sApp.ensureEngineConnectionInternal()
            } else {
                false
            }
        }
    }

    private val engineConnection = EngineConnection()

    override fun attachBaseContext(base: Context?) {
        try {
            super.attachBaseContext(base)

            sApp = this
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
            ensureEngineConnectionInternal()
        } catch (e: Exception) {
            Log.e("App", "Error in onCreate: ${e.message}")
        }
    }

    private fun ensureEngineConnectionInternal(): Boolean {
        try {
            if (EngineConnection.isConnected()) {
                return true
            }

            // Check if Engine APK is installed
            if (!EngineLoader.isEngineInstalled(mContext)) {
                Log.w("App", "Engine APK not installed. Some features will be unavailable.")
                return false
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

            return bound
        } catch (e: Exception) {
            Log.e("App", "Error initializing Engine connection: ${e.message}")
            return false
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
