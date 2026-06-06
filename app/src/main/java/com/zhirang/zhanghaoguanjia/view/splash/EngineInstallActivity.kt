package com.zhirang.zhanghaoguanjia.view.splash

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zhirang.zhanghaoguanjia.databinding.ActivityEngineInstallBinding
import com.zhirang.zhanghaoguanjia.engine.EngineConnection
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.engine.EngineInstallReceiver
import com.zhirang.zhanghaoguanjia.engine.EngineLoader
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.view.home.HomeActivity
import com.zhirang.zhanghaoguanjia.view.login.LoginActivity

/**
 * EngineInstallActivity is the entry point for first-time setup.
 * It checks if the Engine APK is installed, and if not, guides the user
 * through copying it from assets and installing it.
 *
 * After successful installation, it redirects to the current login/home flow.
 */
class EngineInstallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EngineInstallActivity"
        private const val PREFS_NAME = "engine_setup_prefs"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val BIND_RETRY_COUNT = 3
        private const val BIND_RETRY_DELAY_MS = 2000L

        /**
         * Check if the first-time setup has been completed.
         */
        fun isSetupComplete(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SETUP_COMPLETE, false)
        }

        /**
         * Mark setup as complete.
         */
        fun markSetupComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SETUP_COMPLETE, true)
                .apply()
        }

        /**
         * Reset setup flag (for testing).
         */
        fun resetSetup(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SETUP_COMPLETE)
                .apply()
        }
    }

    private lateinit var binding: ActivityEngineInstallBinding
    private val handler = Handler(Looper.getMainLooper())
    private var installReceiver: BroadcastReceiver? = null
    private var isWaitingForInstall = false
    private var installTimeoutRunnable: Runnable? = null
    private var installStartTime = 0L

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkInstallPermissionAndProceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEngineInstallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if Engine is already installed and setup is complete
        if (EngineInstaller.isEngineInstalled(this) && isSetupComplete(this)) {
            Log.d(TAG, "Engine installed and setup complete, redirecting")
            redirectToMain()
            return
        }

        // Setup UI click listeners
        binding.retryButton.setOnClickListener { startSetupFlow() }
        binding.grantPermissionButton.setOnClickListener { requestInstallPermission() }
        binding.exitButton.setOnClickListener { finish() }

        // Start setup flow
        startSetupFlow()
    }

    override fun onResume() {
        super.onResume()
        // Always check install result on resume — MIUI may not send reliable broadcasts
        if (isWaitingForInstall || EngineInstaller.isEngineInstalled(this)) {
            handler.postDelayed({ checkInstallResult() }, 800)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterInstallReceiver()
        cancelInstallTimeout()
    }

    private fun startInstallTimeout() {
        cancelInstallTimeout()
        installStartTime = System.currentTimeMillis()
        val timeoutRunnable = Runnable {
            if (isWaitingForInstall) {
                Log.w(TAG, "Install timeout reached (${EngineInstaller.INSTALL_TIMEOUT_MS}ms), forcing check")
                checkInstallResult()
            }
        }
        installTimeoutRunnable = timeoutRunnable
        handler.postDelayed(timeoutRunnable, EngineInstaller.INSTALL_TIMEOUT_MS)
    }

    private fun cancelInstallTimeout() {
        installTimeoutRunnable?.let { handler.removeCallbacks(it) }
        installTimeoutRunnable = null
    }

    private fun startSetupFlow() {
        when {
            EngineInstaller.isEngineInstalled(this) -> {
                // Engine is installed, verify it works
                showState(State.VERIFYING)
                verifyEngineAndProceed()
            }
            !EngineInstaller.canInstallUnknownApps(this) -> {
                // Need install unknown apps permission
                showState(State.NEED_PERMISSION)
            }
            else -> {
                // Proceed with installation
                installEngineFromAssets()
            }
        }
    }

    private fun installEngineFromAssets() {
        lifecycleScope.launch {
            showState(State.COPYING)

            val result = withContext(Dispatchers.IO) {
                EngineInstaller.installFromAssets(this@EngineInstallActivity)
            }

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Engine APK copied successfully, triggering install")
                    showState(State.INSTALLING)
                    isWaitingForInstall = true
                    registerInstallReceiver()
                    startInstallTimeout()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to copy Engine APK: ${error.message}")
                    showError("准备引擎失败: ${error.message}")
                }
            )
        }
    }

    private fun checkInstallResult() {
        isWaitingForInstall = false
        cancelInstallTimeout()
        if (EngineInstaller.isEngineInstalled(this)) {
            Log.d(TAG, "Engine installation detected")
            showState(State.VERIFYING)
            verifyEngineAndProceed()
        } else {
            val elapsed = System.currentTimeMillis() - installStartTime
            Log.w(TAG, "Engine not installed after ${elapsed}ms")
            showError("引擎未安装完成，请重试")
        }
    }

    private fun verifyEngineAndProceed() {
        lifecycleScope.launch {
            var success = false
            var attempts = 0

            while (attempts < BIND_RETRY_COUNT && !success) {
                success = try {
                    withContext(Dispatchers.IO) {
                        // Initialize EngineLoader
                        val loaderSuccess = EngineLoader.init(this@EngineInstallActivity)
                        if (!loaderSuccess) {
                            Log.w(TAG, "EngineLoader init failed on attempt ${attempts + 1}")
                            return@withContext false
                        }

                        // Try to bind to Engine Service
                        val connection = EngineConnection()
                        val bound = connection.bind(this@EngineInstallActivity)
                        if (!bound) {
                            Log.w(TAG, "Engine bind failed on attempt ${attempts + 1}")
                            return@withContext false
                        }

                        // Wait a moment for service connection
                        kotlinx.coroutines.delay(BIND_RETRY_DELAY_MS)

                        val connected = EngineConnection.isConnected()
                        if (connected) {
                            connection.unbind(this@EngineInstallActivity)
                        }
                        connected
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying Engine on attempt ${attempts + 1}: ${e.message}")
                    false
                }

                if (!success) {
                    attempts++
                    if (attempts < BIND_RETRY_COUNT) {
                        kotlinx.coroutines.delay(BIND_RETRY_DELAY_MS)
                    }
                }
            }

            if (success) {
                Log.i(TAG, "Engine verified successfully")
                markSetupComplete(this@EngineInstallActivity)
                showState(State.SUCCESS)
                handler.postDelayed({ redirectToMain() }, 1500)
            } else {
                val errorMsg = if (isMiui()) {
                            "MIUI 阻止了引擎服务连接。\n请前往「设置 → 应用管理 → 账号管家引擎 → 自启动管理」开启权限，然后点击重试。"
                } else {
                    "引擎安装完成但无法连接，请检查是否安装正确"
                }
                showError(errorMsg)
            }
        }
    }

    private fun redirectToMain() {
        try {
            val intent = if (TokenManager.getInstance().isLoggedIn()) {
                Intent(this, HomeActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error redirecting after engine install: ${e.message}")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    /**
     * Detect if running on MIUI (Xiaomi/Redmi/Poco devices).
     * MIUI has a WakePathChecker that blocks cross-app Service binding
     * unless the callee app has autostart permission enabled.
     */
    private fun isMiui(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER
        val brand = android.os.Build.BRAND
        return manufacturer.equals("Xiaomi", ignoreCase = true) ||
                manufacturer.equals("Redmi", ignoreCase = true) ||
                brand.equals("Xiaomi", ignoreCase = true) ||
                brand.equals("Redmi", ignoreCase = true)
    }

    private fun requestInstallPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                installPermissionLauncher.launch(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting install permission: ${e.message}")
            showError("无法打开权限设置，请手动授予")
        }
    }

    private fun checkInstallPermissionAndProceed() {
        if (EngineInstaller.canInstallUnknownApps(this)) {
            Log.d(TAG, "Install permission granted")
            installEngineFromAssets()
        } else {
            Log.w(TAG, "Install permission still not granted")
            showState(State.NEED_PERMISSION)
        }
    }

    private fun registerInstallReceiver() {
        try {
            unregisterInstallReceiver()
            installReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_PACKAGE_ADDED,
                        Intent.ACTION_PACKAGE_REPLACED -> {
                            val packageName = intent.data?.schemeSpecificPart
                            if (packageName == EngineInstaller.ENGINE_PACKAGE) {
                                Log.i(TAG, "Engine package install/replace detected")
                                isWaitingForInstall = false
                                unregisterInstallReceiver()
                                showState(State.VERIFYING)
                                verifyEngineAndProceed()
                            }
                        }
                        EngineInstallReceiver.ACTION_ENGINE_INSTALL_COMPLETE -> {
                            val success = intent.getBooleanExtra(EngineInstallReceiver.EXTRA_SUCCESS, false)
                            if (success) {
                                Log.i(TAG, "Internal install-complete broadcast received")
                                isWaitingForInstall = false
                                unregisterInstallReceiver()
                                showState(State.VERIFYING)
                                verifyEngineAndProceed()
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(EngineInstallReceiver.ACTION_ENGINE_INSTALL_COMPLETE)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(installReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(installReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error registering install receiver: ${e.message}")
        }
    }

    private fun unregisterInstallReceiver() {
        try {
            installReceiver?.let {
                unregisterReceiver(it)
                installReceiver = null
            }
        } catch (_: Exception) {
        }
    }

    private fun showError(message: String) {
        showState(State.ERROR)
        binding.errorMessage.text = message
    }

    private fun showState(state: State) {
        // Hide all states first
        binding.progressLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.GONE
        binding.permissionLayout.visibility = View.GONE
        binding.successLayout.visibility = View.GONE

        when (state) {
            State.COPYING -> {
                binding.progressLayout.visibility = View.VISIBLE
                binding.statusText.text = "正在准备引擎..."
                binding.progressBar.isIndeterminate = true
            }
            State.INSTALLING -> {
                binding.progressLayout.visibility = View.VISIBLE
                binding.statusText.text = "正在安装引擎，请按提示完成安装..."
                binding.progressBar.isIndeterminate = true
            }
            State.VERIFYING -> {
                binding.progressLayout.visibility = View.VISIBLE
                binding.statusText.text = "安装完成，正在启动..."
                binding.progressBar.isIndeterminate = true
            }
            State.SUCCESS -> {
                binding.successLayout.visibility = View.VISIBLE
            }
            State.ERROR -> {
                binding.errorLayout.visibility = View.VISIBLE
            }
            State.NEED_PERMISSION -> {
                binding.permissionLayout.visibility = View.VISIBLE
            }
        }
    }

    private enum class State {
        COPYING,
        INSTALLING,
        VERIFYING,
        SUCCESS,
        ERROR,
        NEED_PERMISSION
    }
}
