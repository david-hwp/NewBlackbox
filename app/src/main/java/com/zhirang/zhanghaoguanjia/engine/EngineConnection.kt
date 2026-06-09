package com.zhirang.zhanghaoguanjia.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import top.niunaijun.blackbox.engine.IBlackBoxEngine

/**
 * EngineConnection manages the ServiceConnection lifecycle for binding to the Engine Service.
 */
class EngineConnection : ServiceConnection {

    companion object {
        private const val TAG = "EngineConnection"
        private const val WAKE_RETRY_DELAY_MS = 800L
        private val handler = Handler(Looper.getMainLooper())

        @Volatile
        var engine: IBlackBoxEngine? = null
            private set

        fun isConnected(): Boolean = engine != null
    }

    private val callbacks = mutableListOf<() -> Unit>()

    /**
     * Bind to the Engine Service. Returns true if bind request was sent successfully.
     */
    fun bind(context: Context): Boolean {
        return bindInternal(context, allowWakeRetry = true)
    }

    private fun bindInternal(context: Context, allowWakeRetry: Boolean): Boolean {
        return try {
            val enginePackage = EngineIdentity.packageName
            if (isConnected()) {
                Log.d(TAG, "Already connected to Engine: $enginePackage")
                return true
            }

            val intent = Intent().apply {
                component = EngineIdentity.component(EngineIdentity.ENGINE_SERVICE_CLASS)
            }

            val bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE)
            if (bound) {
                Log.d(TAG, "Binding to Engine Service requested: $enginePackage")
            } else {
                Log.w(TAG, "Failed to bind to Engine Service: $enginePackage")
                if (allowWakeRetry) {
                    wakeEngine(context)
                    val appContext = context.applicationContext ?: context
                    handler.postDelayed({
                        if (!isConnected()) {
                            bindInternal(appContext, allowWakeRetry = false)
                        }
                    }, WAKE_RETRY_DELAY_MS)
                }
            }
            bound
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to Engine Service: ${e.message}", e)
            false
        }
    }

    private fun wakeEngine(context: Context) {
        try {
            val intent = Intent().apply {
                component = EngineIdentity.component(EngineIdentity.ENGINE_WAKE_ACTIVITY_CLASS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(intent)
            Log.d(TAG, "Engine wake activity requested: ${EngineIdentity.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request Engine wake activity for ${EngineIdentity.packageName}: ${e.message}")
        }
    }

    /**
     * Unbind from the Engine Service.
     */
    fun unbind(context: Context) {
        try {
            if (isConnected()) {
                context.unbindService(this)
                Log.d(TAG, "Unbound from Engine Service")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding from Engine Service: ${e.message}")
        }
        engine = null
    }

    /**
     * Add a callback to be invoked when the Engine becomes connected.
     */
    fun addCallback(callback: () -> Unit) {
        synchronized(callbacks) {
            callbacks.add(callback)
        }
        // If already connected, invoke immediately
        if (isConnected()) {
            try {
                callback()
            } catch (e: Exception) {
                Log.w(TAG, "Callback invocation failed: ${e.message}")
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            engine = IBlackBoxEngine.Stub.asInterface(service)
            Log.d(TAG, "Engine Service connected: $name")
            EngineProxy.init(engine!!)

            synchronized(callbacks) {
                callbacks.forEach { callback ->
                    try {
                        callback()
                    } catch (e: Exception) {
                        Log.w(TAG, "Service connected callback failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected: ${e.message}", e)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.w(TAG, "Engine Service disconnected: $name")
        engine = null
        EngineProxy.disconnect()
    }
}
