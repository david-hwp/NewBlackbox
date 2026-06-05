package top.niunaijun.blackboxa.view.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.ActivityEngineSwitchBinding
import top.niunaijun.blackboxa.engine.EngineInstaller

class EngineSwitchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEngineSwitchBinding
    private lateinit var viewModel: EngineSwitchViewModel
    private lateinit var adapter: EngineVersionAdapter

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
        viewModel.loadVersions()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.updateCurrentVersion(EngineInstaller.getInstalledEngineVersion(this))
        }
    }
}
