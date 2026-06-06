package com.zhirang.zhanghaoguanjia.view.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.bean.dto.EngineVersionDto
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
            showUpgradeDetailDialog(version)
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
                    Toast.makeText(this, "引擎升级成功：${pendingInstallVersionName ?: targetVersion}", Toast.LENGTH_LONG).show()
                    pendingInstallVersionCode = null
                    pendingInstallVersionName = null
                } else {
                    Toast.makeText(this, "引擎升级未完成，请确认系统安装弹窗", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showUpgradeDetailDialog(version: EngineVersionDto) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_engine_version_confirm, null)
        content.findViewById<TextView>(R.id.tvVersionName).text = version.versionName
        content.findViewById<TextView>(R.id.tvVersionCode).text = "版本号：${version.versionCode}"
        content.findViewById<TextView>(R.id.tvPublishedAt).text = "发布时间：${formatDateTime(version.createdAt)}"
        content.findViewById<TextView>(R.id.tvChangelog).text =
            version.changelog?.takeIf { it.isNotBlank() } ?: "暂无更新日志"

        MaterialAlertDialogBuilder(this)
            .setTitle("升级引擎")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("升级") { _, _ ->
                showIrreversibleConfirmDialog(version)
            }
            .show()
    }

    private fun showIrreversibleConfirmDialog(version: EngineVersionDto) {
        MaterialAlertDialogBuilder(this)
            .setTitle("注意！！！")
            .setMessage("升级引擎后不可回退，确认升级吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认升级") { _, _ ->
                startUpgrade(version)
            }
            .show()
    }

    private fun startUpgrade(version: EngineVersionDto) {
        if (!EngineInstaller.canInstallUnknownApps(this)) {
            Toast.makeText(this, "请先允许安装未知应用", Toast.LENGTH_SHORT).show()
            EngineInstaller.openInstallPermissionSettings(this)
            return
        }
        viewModel.downloadAndInstall(version)
    }

    private fun formatDateTime(value: String?): String {
        return value?.takeIf { it.isNotBlank() }
            ?.replace('T', ' ')
            ?.take(19)
            ?: "-"
    }
}
