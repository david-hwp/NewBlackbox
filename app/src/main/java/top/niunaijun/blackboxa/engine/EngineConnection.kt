package top.niunaijun.blackboxa.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import top.niunaijun.blackbox.engine.IBlackBoxEngine

/**
 * EngineConnection manages the ServiceConnection lifecycle for binding to the Engine Service.
 */
class EngineConnection : ServiceConnection {

    companion object {
        private const val TAG = "EngineConnection"
        private const val ENGINE_PACKAGE = "top.niunaijun.blackbox.engine"
        private const val ENGINE_SERVICE = "top.niunaijun.blackbox.engine.BlackBoxEngineService"

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
        return try {
            if (isConnected()) {
                Log.d(TAG, "Already connected to Engine")
                return true
            }

            val intent = Intent().apply {
                component = ComponentName(ENGINE_PACKAGE, ENGINE_SERVICE)
            }

            val bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE)
            if (bound) {
                Log.d(TAG, "Binding to Engine Service requested")
            } else {
                Log.w(TAG, "Failed to bind to Engine Service")
            }
            bound
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to Engine Service: ${e.message}", e)
            false
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
