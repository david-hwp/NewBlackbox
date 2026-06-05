package com.zhirang.zhanghaoguanjia.view.profile

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zhirang.zhanghaoguanjia.databinding.ActivityProfileBinding
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
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
            LoginActivity.start(this)
            finish()
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
                val username = getDisplayUsername(it.username)
                binding.tvAvatarInitial?.text = username.take(1)
                binding.tvUsername?.text = username
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
    }

    private fun getDisplayUsername(username: String?): String {
        return username?.takeIf { it.isNotBlank() } ?: "我的账号"
    }

    private fun showEditUsernameSheet() {
        val currentUsername = binding.tvUsername?.text?.toString().orEmpty()
        val sheet = EditUsernameSheetFragment.newInstance(currentUsername)
        sheet.setOnSaveListener { username ->
            viewModel.updateUsername(username)
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
            .setPositiveButton("确定", null)
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
