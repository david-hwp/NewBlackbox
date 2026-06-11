package com.zhirang.zhanghaoguanjia.view.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.databinding.ActivityLoginBinding
import com.zhirang.zhanghaoguanjia.databinding.DialogRegisterAccountBinding
import com.zhirang.zhanghaoguanjia.view.home.HomeActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel
    private var registerDialog: AlertDialog? = null
    private var registerBinding: DialogRegisterAccountBinding? = null

    companion object {
        private val PHONE_PATTERN = Regex("^1[3-9]\\d{9}$")

        fun start(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }

        fun startClearingTask(context: Context) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if already logged in
        if (TokenManager.getInstance().isLoggedIn()) {
            HomeActivity.start(this)
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        initListeners()
        observeViewModel()
    }

    private fun initListeners() {
        setupPasswordToggle(binding.etPassword, binding.btnToggleLoginPassword)

        binding.btnLogin.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入手机号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(phone, password)
        }

        binding.tvRegister.setOnClickListener {
            showRegisterDialog()
        }
    }

    private fun showRegisterDialog() {
        val dialogBinding = DialogRegisterAccountBinding.inflate(layoutInflater)
        registerBinding = dialogBinding
        dialogBinding.etRegisterPhone.setText(binding.etPhone.text?.toString().orEmpty())
        dialogBinding.etRegisterPassword.setText(binding.etPassword.text?.toString().orEmpty())
        setupPasswordToggle(dialogBinding.etRegisterPassword, dialogBinding.btnToggleRegisterPassword)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setNegativeButton("取消", null)
            .setPositiveButton("注册", null)
            .create()
            .apply {
                setOnDismissListener {
                    registerDialog = null
                    registerBinding = null
                }
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val phone = dialogBinding.etRegisterPhone.text?.toString()?.trim().orEmpty()
                        val username = dialogBinding.etRegisterUsername.text?.toString()?.trim().orEmpty()
                        val password = dialogBinding.etRegisterPassword.text?.toString()?.trim().orEmpty()
                        if (!validateRegisterForm(dialogBinding, phone, username, password)) {
                            return@setOnClickListener
                        }
                        viewModel.register(phone, password, username)
                    }
                }
            }
        registerDialog = dialog
        dialog.show()
    }

    private fun validateRegisterForm(
        dialogBinding: DialogRegisterAccountBinding,
        phone: String,
        username: String,
        password: String
    ): Boolean {
        val phoneValid = PHONE_PATTERN.matches(phone)
        val usernameValid = username.isNotBlank() && username.length <= 32
        val passwordValid = password.length in 6..64

        showFieldError(dialogBinding.tvRegisterPhoneError, !phoneValid, getString(R.string.error_phone_invalid))
        showFieldError(
            dialogBinding.tvRegisterUsernameError,
            !usernameValid,
            getString(if (username.isBlank()) R.string.error_username_empty else R.string.error_username_length)
        )
        showFieldError(dialogBinding.tvRegisterPasswordError, !passwordValid, getString(R.string.error_password_length))

        return phoneValid && usernameValid && passwordValid
    }

    private fun showFieldError(view: TextView, visible: Boolean, message: String) {
        view.text = message
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setupPasswordToggle(input: EditText, button: ImageButton) {
        setPasswordVisible(input, button, visible = false)
        button.setOnClickListener {
            val nextVisible = input.transformationMethod is PasswordTransformationMethod
            setPasswordVisible(input, button, nextVisible)
        }
    }

    private fun setPasswordVisible(input: EditText, button: ImageButton, visible: Boolean) {
        val cursorPosition = input.selectionStart.coerceAtLeast(0)
        input.transformationMethod = if (visible) null else PasswordTransformationMethod.getInstance()
        input.setSelection(cursorPosition.coerceAtMost(input.text?.length ?: 0))
        button.setImageResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
        button.contentDescription = getString(if (visible) R.string.hide_password else R.string.show_password)
    }

    private fun observeViewModel() {
        viewModel.loginResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = {
                    HomeActivity.start(this)
                    finish()
                },
                onFailure = {
                    // Error is handled by errorLiveData
                }
            )
        }

        viewModel.loadingLiveData.observe(this) { isLoading ->
            binding.btnLogin.isEnabled = !isLoading
            binding.tvRegister.isEnabled = !isLoading
            binding.btnLogin.text = if (isLoading) "登录中…" else "登录"
            registerDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !isLoading
            registerDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.text = if (isLoading) "注册中…" else "注册"
            registerDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !isLoading
        }

        viewModel.registerResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = {
                    val phone = registerBinding?.etRegisterPhone?.text?.toString()?.trim().orEmpty()
                    if (phone.isNotBlank()) {
                        binding.etPhone.setText(phone)
                        getSharedPreferences("subscription_gift_prompt", Context.MODE_PRIVATE)
                            .edit()
                            .putString("pending_gift_phone", phone)
                            .apply()
                    }
                    binding.etPassword.text?.clear()
                    registerDialog?.dismiss()
                    Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                }
            )
        }

        viewModel.errorLiveData.observe(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
