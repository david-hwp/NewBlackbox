package com.zhirang.zhanghaoguanjia.view.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.databinding.ActivityEngineSwitchBinding
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller

class EngineSwitchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEngineSwitchBinding
    private lateinit var viewModel: EngineSwitchViewModel
    private lateinit var adapter: EngineVersionAdapter
    private var pendingInstallVersionCode: Int? = null
    private var pendingInstallVersionName: String? = null
    private var awaitingInstallerReturn = false

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, EngineSwitchActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Duodian)
        binding = ActivityEngineSwitchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[EngineSwitchViewModel::class.java]
        adapter = EngineVersionAdapter(EngineInstaller.getInstalledEngineVersion(this)) { version ->
            if (!EngineInstaller.canInstallUnknownApps(this)) {
                Toast.makeText(this, "请先允许安装未知应用", Toast.LENGTH_SHORT).show()
                EngineInstaller.openInstallPermissionSettings(this)
                return@EngineVersionAdapter
            }
            viewModel.downloadAndInstall(version)
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.versionsLiveData.observe(this) { adapter.submitList(it) }
        viewModel.loadingLiveData.observe(this) { binding.progressBar.visibility = if (it) android.view.View.VISIBLE else android.view.View.GONE }
        viewModel.messageLiveData.observe(this) { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        viewModel.installStartedLiveData.observe(this) { version ->
            pendingInstallVersionCode = version.versionCode
            pendingInstallVersionName = version.versionName
            awaitingInstallerReturn = true
        }
        viewModel.loadVersions()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            val currentVersion = EngineInstaller.getInstalledEngineVersion(this)
            adapter.updateCurrentVersion(currentVersion)
            val targetVersion = pendingInstallVersionCode
            if (awaitingInstallerReturn && targetVersion != null) {
                awaitingInstallerReturn = false
                if (currentVersion == targetVersion) {
                    Toast.makeText(this, "引擎切换成功：${pendingInstallVersionName ?: targetVersion}", Toast.LENGTH_LONG).show()
                    pendingInstallVersionCode = null
                    pendingInstallVersionName = null
                } else {
                    Toast.makeText(this, "引擎切换未完成，请确认系统安装弹窗", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
