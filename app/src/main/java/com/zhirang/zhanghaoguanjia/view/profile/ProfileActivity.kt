package com.zhirang.zhanghaoguanjia.view.profile

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zhirang.zhanghaoguanjia.bean.dto.AppVersionDto
import com.zhirang.zhanghaoguanjia.bean.dto.UserDto
import com.zhirang.zhanghaoguanjia.data.BaseRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.databinding.ActivityProfileBinding
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.update.AppUpdateManager
import com.zhirang.zhanghaoguanjia.util.AvatarImageLoader
import com.zhirang.zhanghaoguanjia.view.gift.GiftActivity
import com.zhirang.zhanghaoguanjia.view.home.HomeActivity
import com.zhirang.zhanghaoguanjia.view.logs.LogsActivity
import com.zhirang.zhanghaoguanjia.view.login.LoginActivity
import com.zhirang.zhanghaoguanjia.view.setting.SettingActivity
import com.zhirang.zhanghaoguanjia.view.dialog.EditUsernameSheetFragment
import com.zhirang.zhanghaoguanjia.view.dialog.ChangePasswordSheetFragment

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var viewModel: ProfileViewModel
    private var currentProfile: UserDto? = null
    private var authReceiverRegistered = false
    private val authExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BaseRepository.ACTION_AUTH_EXPIRED) {
                redirectToLogin()
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ProfileActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(com.zhirang.zhanghaoguanjia.R.style.Theme_Duodian)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        initToolbar()
        initMenuListeners()
        initBottomNav()
        observeViewModel()

        viewModel.loadProfile()
        viewModel.refreshEngineUpgradeState()
        viewModel.refreshAppUpdateState()
    }

    override fun onStart() {
        super.onStart()
        registerAuthReceiver()
    }

    override fun onStop() {
        super.onStop()
        if (authReceiverRegistered) {
            unregisterReceiver(authExpiredReceiver)
            authReceiverRegistered = false
        }
    }

    private fun initToolbar() {
        binding.toolbar?.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initMenuListeners() {
        binding.menuGift?.setOnClickListener {
            GiftActivity.start(this)
        }

        binding.menuLogs?.setOnClickListener {
            LogsActivity.start(this)
        }

        binding.btnEditUsername?.setOnClickListener {
            showEditUsernameSheet()
        }
        binding.avatarContainer?.setOnClickListener {
            showEditUsernameSheet()
        }

        binding.menuChangePassword?.setOnClickListener {
            showChangePasswordSheet()
        }

        binding.menuFeedback.setOnClickListener {
            FeedbackActivity.start(this)
        }

        binding.menuSoftwareSettings.setOnClickListener {
            SettingActivity.start(this)
        }

        binding.menuEngineSwitch.setOnClickListener {
            EngineSwitchActivity.start(this)
        }

        binding.menuAbout.setOnClickListener {
            showAboutDialog()
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            LoginActivity.startClearingTask(this)
        }
    }

    private fun initBottomNav() {
        binding.navHome?.setOnClickListener {
            HomeActivity.start(this)
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.userProfileLiveData.observe(this) { profile ->
            profile?.let {
                currentProfile = it
                val username = getDisplayUsername(it.username)
                AvatarImageLoader.bind(
                    imageView = binding.ivAvatar,
                    fallbackView = binding.tvAvatarInitial,
                    avatarUrl = it.avatarUrl,
                    initial = username
                )
                binding.tvUsername?.text = username
                binding.tvAdminTag?.visibility = if (it.role.equals("ADMIN", ignoreCase = true)) View.VISIBLE else View.GONE
                binding.tvPhone?.text = maskPhone(it.phone)
                binding.tvShopCount?.text = it.shopCount.toString()
                binding.tvPlatformCount?.text = it.platformCount.toString()
                binding.tvComputeBalance?.text = it.computeBalance.toString()
            }
        }

        viewModel.errorLiveData.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.updateResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = {
                    Toast.makeText(this, "修改成功", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(this, e.message ?: "修改失败", Toast.LENGTH_SHORT).show()
                }
            )
        }

        viewModel.passwordResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = {
                    Toast.makeText(this, "密码修改成功，请重新登录", Toast.LENGTH_SHORT).show()
                    viewModel.logout()
                    LoginActivity.start(this)
                    finish()
                },
                onFailure = { e ->
                    Toast.makeText(this, e.message ?: "密码修改失败", Toast.LENGTH_SHORT).show()
                }
            )
        }

        viewModel.hasEngineUpgradeLiveData.observe(this) { hasUpgrade ->
            binding.tvEngineUpgradeNew?.visibility = if (hasUpgrade) View.VISIBLE else View.GONE
        }

        viewModel.hasAppUpdateLiveData.observe(this) { hasUpdate ->
            binding.tvAboutNew?.visibility = if (hasUpdate) View.VISIBLE else View.GONE
        }

        viewModel.appUpdateLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = { version ->
                    if (version == null) {
                        Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                    } else {
                        showAppUpdateDialog(version)
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this, e.message ?: "检查更新失败", Toast.LENGTH_SHORT).show()
                }
            )
        }

        viewModel.appInstallResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = {
                    Toast.makeText(this, "已开始安装，请在系统弹窗中确认", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(this, e.message ?: "安装启动失败", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (!TokenManager.getInstance().isLoggedIn()) {
            redirectToLogin()
            return
        }
        if (::viewModel.isInitialized) {
            viewModel.refreshEngineUpgradeState()
            viewModel.refreshAppUpdateState()
        }
    }

    private fun registerAuthReceiver() {
        if (authReceiverRegistered) {
            return
        }
        val filter = IntentFilter(BaseRepository.ACTION_AUTH_EXPIRED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(authExpiredReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(authExpiredReceiver, filter)
        }
        authReceiverRegistered = true
    }

    private fun redirectToLogin() {
        if (isFinishing || isDestroyed) {
            return
        }
        LoginActivity.startClearingTask(this)
        finish()
    }

    private fun getDisplayUsername(username: String?): String {
        return username?.takeIf { it.isNotBlank() } ?: "我的账号"
    }

    private fun showEditUsernameSheet() {
        val profile = currentProfile
        val currentUsername = binding.tvUsername?.text?.toString().orEmpty()
        val sheet = EditUsernameSheetFragment.newInstance(currentUsername, profile?.avatarUrl)
        sheet.setOnSaveListener { username, avatarUri, avatarUrl ->
            viewModel.updateProfile(username, avatarUri, avatarUrl)
        }
        sheet.show(supportFragmentManager, "EditUsername")
    }

    private fun showChangePasswordSheet() {
        val sheet = ChangePasswordSheetFragment()
        sheet.setOnConfirmListener { oldPwd, newPwd, confirmPwd ->
            viewModel.updatePassword(oldPwd, newPwd, confirmPwd)
        }
        sheet.show(supportFragmentManager, "ChangePassword")
    }

    private fun showAboutDialog() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val mainVersion = "${packageInfo.versionName} ($versionCode)"
        val engineVersionCode = EngineInstaller.getInstalledEngineVersion(this)
        val engineVersionName = EngineInstaller.getInstalledEngineVersionName(this)
        val engineVersion = if (engineVersionCode > 0) {
            engineVersionName?.let { "$it ($engineVersionCode)" } ?: engineVersionCode.toString()
        } else {
            "未安装"
        }
        val content = """
            版本号：$mainVersion
            引擎版本：$engineVersion
            服务商信息：长沙智壤软件技术有限公司
        """.trimIndent()
        MaterialAlertDialogBuilder(this)
            .setTitle("关于")
            .setMessage(content)
            .setNegativeButton("检查更新") { _, _ ->
                viewModel.checkAppUpdate()
            }
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showAppUpdateDialog(version: AppVersionDto) {
        val changelog = version.changelog?.takeIf { it.isNotBlank() } ?: "暂无更新说明"
        val content = """
            当前版本：${AppUpdateManager.currentVersionLabel(this)}
            最新版本：${version.versionName} (${version.versionCode})

            $changelog
        """.trimIndent()
        MaterialAlertDialogBuilder(this)
            .setTitle("发现新版本")
            .setMessage(content)
            .setNegativeButton("稍后", null)
            .setPositiveButton("立即升级") { _, _ ->
                viewModel.downloadAndInstallApp(version)
            }
            .show()
    }

    private fun maskPhone(phone: String?): String {
        if (phone.isNullOrBlank()) {
            return ""
        }

        return if (phone.length == 11) {
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        } else {
            phone
        }
    }
}
