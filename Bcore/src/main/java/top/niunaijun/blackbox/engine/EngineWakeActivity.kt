package top.niunaijun.blackbox.engine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import top.niunaijun.blackbox.utils.Slog

class EngineWakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startService(Intent(this, BlackBoxEngineService::class.java))
            Slog.d(TAG, "Engine wake requested")
        } catch (e: Exception) {
            Slog.w(TAG, "Failed to wake Engine service: ${e.message}")
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "EngineWakeActivity"
    }
}
