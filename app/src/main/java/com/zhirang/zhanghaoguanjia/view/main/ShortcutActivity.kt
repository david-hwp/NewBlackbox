package com.zhirang.zhanghaoguanjia.view.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.engine.EnginePermissionCenter
import com.zhirang.zhanghaoguanjia.engine.EngineProxy
import kotlinx.coroutines.launch

class ShortcutActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private var targetPackage: String? = null
    private var targetUserId: Int = 0
    private lateinit var enginePermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initEnginePermissionLauncher()

        val pkg = intent.getStringExtra("pkg")?.takeIf { it.isNotBlank() }
        val userId = intent.getIntExtra("userId", 0)
        if (pkg == null) {
            finishOnce()
            return
        }
        targetPackage = pkg
        targetUserId = userId

        if (!ensureEnginePermissions(pkg)) {
            return
        }
        launchWhenEngineReady()
    }

    private fun initEnginePermissionLauncher() {
        enginePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val pkg = targetPackage
            if (pkg == null || result.resultCode != RESULT_OK) {
                finishOnce()
                return@registerForActivityResult
            }
            val requiredPermissions = EnginePermissionCenter.platformRequiredPermissions(this, pkg)
            if (!EnginePermissionCenter.hasEnginePermissions(this, requiredPermissions)) {
                finishOnce()
                return@registerForActivityResult
            }
            launchWhenEngineReady()
        }
    }

    private fun ensureEnginePermissions(pkg: String): Boolean {
        val requiredPermissions = EnginePermissionCenter.platformRequiredPermissions(this, pkg)
        if (EnginePermissionCenter.hasEnginePermissions(this, requiredPermissions)) {
            return true
        }
        return try {
            enginePermissionLauncher.launch(EnginePermissionCenter.buildPlatformIntent(this, pkg))
            false
        } catch (e: Exception) {
            finishOnce()
            false
        }
    }

    private fun launchWhenEngineReady() {
        if (EngineProxy.isConnected()) {
            launchShortcut()
            return
        }

        EngineProxy.addServiceAvailableCallback {
            runOnUiThread { launchShortcut() }
        }
        App.ensureEngineConnection()
        handler.postDelayed({ finishOnce() }, 8000L)
    }

    private fun launchShortcut() {
        if (finished) {
            return
        }
        val pkg = targetPackage ?: run {
            finishOnce()
            return
        }
        lifecycleScope.launch {
            val launchIntent = EngineProxy.getLaunchIntent(pkg, targetUserId)
            if (launchIntent != null && !finished) {
                startActivity(launchIntent)
            }
            finishOnce()
        }
    }

    private fun finishOnce() {
        if (finished) {
            return
        }
        finished = true
        if (!isFinishing) {
            finish()
        }
    }
}
