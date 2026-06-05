package top.niunaijun.blackboxa.view.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import top.niunaijun.blackboxa.databinding.ActivityProfileBinding
import top.niunaijun.blackboxa.view.gift.GiftActivity
import top.niunaijun.blackboxa.view.home.HomeActivity
import top.niunaijun.blackboxa.view.logs.LogsActivity
import top.niunaijun.blackboxa.view.login.LoginActivity
import top.niunaijun.blackboxa.view.setting.SettingActivity
import top.niunaijun.blackboxa.view.dialog.EditUsernameSheetFragment
import top.niunaijun.blackboxa.view.dialog.ChangePasswordSheetFragment

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
        setTheme(top.niunaijun.blackboxa.R.style.Theme_Duodian)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        initToolbar()
        initMenuListeners()
        initFeedbackSection()
        initBottomNav()
        observeViewModel()

        viewModel.loadProfile()
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initMenuListeners() {
        binding.menuGift.setOnClickListener {
            GiftActivity.start(this)
        }

        binding.menuLogs.setOnClickListener {
            LogsActivity.start(this)
        }

        binding.menuChangeUsername.setOnClickListener {
            EditUsernameSheetFragment().show(supportFragmentManager, "EditUsername")
        }

        binding.menuChangePassword.setOnClickListener {
            ChangePasswordSheetFragment().show(supportFragmentManager, "ChangePassword")
        }

        binding.menuSettings.setOnClickListener {
            SettingActivity.start(this)
        }
    }

    private fun initFeedbackSection() {
        binding.btnSubmitFeedback.setOnClickListener {
            val content = binding.etFeedback.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入反馈内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.submitFeedback(content, emptyList())
        }

        binding.btnAddImage.setOnClickListener {
            Toast.makeText(this, "图片上传功能开发中", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initBottomNav() {
        binding.navHome.setOnClickListener {
            HomeActivity.start(this)
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.userProfileLiveData.observe(this) { profile ->
            profile?.let {
                binding.tvAvatarInitial.text = it.username.take(1)
                binding.tvUsername.text = it.username
                binding.tvPhone.text = maskPhone(it.phone)
                binding.tvShopCount.text = it.shopCount.toString()
                binding.tvPlatformCount.text = it.platformCount.toString()
                binding.tvComputeBalance.text = it.computeBalance.toString()
            }
        }

        viewModel.feedbackResultLiveData.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(top.niunaijun.blackboxa.R.string.submit_success), Toast.LENGTH_SHORT).show()
                binding.etFeedback.text.clear()
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

    private fun maskPhone(phone: String): String {
        return if (phone.length == 11) {
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        } else {
            phone
        }
    }
}
