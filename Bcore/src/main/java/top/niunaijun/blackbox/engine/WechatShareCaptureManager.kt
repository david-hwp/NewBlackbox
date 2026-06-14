package top.niunaijun.blackbox.engine

import android.os.Handler
import android.os.IBinder
import android.os.Looper
import top.niunaijun.blackbox.core.system.am.WechatShareTargetDispatcher
import top.niunaijun.blackbox.entity.pm.WechatShareTarget
import top.niunaijun.blackbox.utils.Slog
import java.util.concurrent.ConcurrentHashMap

object WechatShareCaptureManager : WechatShareTargetDispatcher.Listener {
    private const val TAG = "WechatShareCaptureManager"
    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val MAX_TIMEOUT_MS = 120_000L
    private val handler = Handler(Looper.getMainLooper())
    private val captures = ConcurrentHashMap<String, CaptureSession>()

    init {
        WechatShareTargetDispatcher.addListener(this)
    }

    fun start(packageName: String?, userId: Int, callback: IWechatShareCaptureCallback?, timeoutMs: Long): Boolean {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return false
        val cb = callback ?: return false
        if (userId < 0) {
            return false
        }
        val key = key(pkg, userId)
        cancel(pkg, userId, notify = false)
        val session = CaptureSession(pkg, userId, cb)
        val death = IBinder.DeathRecipient {
            captures.remove(key)?.clear()
        }
        session.deathRecipient = death
        try {
            cb.asBinder().linkToDeath(death, 0)
        } catch (e: Exception) {
            Slog.w(TAG, "callback linkToDeath failed package=$pkg userId=$userId", e)
            return false
        }
        val timeout = timeoutMs.takeIf { it > 0 }?.coerceAtMost(MAX_TIMEOUT_MS) ?: DEFAULT_TIMEOUT_MS
        val timeoutRunnable = Runnable {
            captures.remove(key)?.let {
                it.clear()
                runCatching { it.callback.onShareTargetTimeout(pkg, userId) }
            }
        }
        session.timeoutRunnable = timeoutRunnable
        captures[key] = session
        handler.postDelayed(timeoutRunnable, timeout)
        Slog.d(TAG, "wechat share capture started package=$pkg userId=$userId timeout=$timeout")
        return true
    }

    fun cancel(packageName: String?, userId: Int) {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return
        cancel(pkg, userId, notify = true)
    }

    override fun onWechatShareTargetCaptured(target: WechatShareTarget?) {
        val captured = target ?: return
        val pkg = captured.packageName ?: return
        val key = key(pkg, captured.userId)
        val session = captures.remove(key) ?: return
        session.clear()
        val resolved = runCatching { WechatShareTargetResolver.resolve(captured) }.getOrElse {
            Slog.w(TAG, "resolve share target failed package=$pkg userId=${captured.userId}", it)
            null
        }
        if (resolved != null) {
            runCatching { session.callback.onShareTargetResolved(resolved) }
        } else {
            runCatching { session.callback.onShareTargetError(pkg, captured.userId, "resolve failed") }
        }
    }

    private fun cancel(packageName: String, userId: Int, notify: Boolean) {
        val session = captures.remove(key(packageName, userId)) ?: return
        session.clear()
        if (notify) {
            runCatching { session.callback.onShareTargetCancelled(packageName, userId) }
        }
    }

    private fun key(packageName: String, userId: Int): String {
        return "$packageName#$userId"
    }

    private data class CaptureSession(
        val packageName: String,
        val userId: Int,
        val callback: IWechatShareCaptureCallback
    ) {
        var timeoutRunnable: Runnable? = null
        var deathRecipient: IBinder.DeathRecipient? = null

        fun clear() {
            timeoutRunnable?.let { WechatShareCaptureManager.handler.removeCallbacks(it) }
            timeoutRunnable = null
            deathRecipient?.let {
                runCatching { callback.asBinder().unlinkToDeath(it, 0) }
            }
            deathRecipient = null
        }
    }
}
