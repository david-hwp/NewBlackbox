package top.niunaijun.blackbox.engine

import android.app.Activity
import android.os.Bundle
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.utils.Slog

class EngineE2eLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_JD_PACKAGE
        val userId = intent.getIntExtra(EXTRA_USER_ID, 0)
        android.util.Log.i(LOG_TAG, "MARK_DIRECT_LAUNCH_START pkg=$targetPackage userId=$userId")
        try {
            val ok = BlackBoxCore.get().launchApk(targetPackage, userId)
            android.util.Log.i(LOG_TAG, "MARK_DIRECT_LAUNCH_RESULT pkg=$targetPackage userId=$userId ok=$ok")
            if (!ok) {
                Slog.w(TAG, "Direct e2e launch returned false pkg=$targetPackage userId=$userId")
            }
        } catch (e: Throwable) {
            android.util.Log.e(
                LOG_TAG,
                "MARK_DIRECT_LAUNCH_ERROR pkg=$targetPackage userId=$userId error=${e.javaClass.name}: ${e.message}",
                e
            )
            Slog.w(TAG, "Direct e2e launch failed pkg=$targetPackage userId=$userId", e)
        } finally {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val TAG = "EngineE2eLaunchActivity"
        private const val LOG_TAG = "E2E_JD_CAPTCHA"
        private const val DEFAULT_JD_PACKAGE = "com.jd.mrd.jingming"
        private const val EXTRA_PACKAGE = "pkg"
        private const val EXTRA_USER_ID = "userId"
    }
}
