package com.zhirang.zhanghaoguanjia.view.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.databinding.ActivityLoginBinding
import com.zhirang.zhanghaoguanjia.view.home.HomeActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
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
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val phoneInput = TextInputEditText(this).apply {
            hint = "手机号"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(binding.etPhone.text?.toString().orEmpty())
        }
        val usernameInput = TextInputEditText(this).apply {
            hint = "用户名"
        }
        val passwordInput = TextInputEditText(this).apply {
            hint = "密码（至少6位）"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(binding.etPassword.text?.toString().orEmpty())
        }
        container.addView(phoneInput)
        container.addView(usernameInput)
        container.addView(passwordInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("注册账号")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("注册", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val phone = phoneInput.text?.toString()?.trim().orEmpty()
                        val username = usernameInput.text?.toString()?.trim().orEmpty()
                        val password = passwordInput.text?.toString()?.trim().orEmpty()
                        if (phone.length != 11 || username.isBlank() || password.length < 6) {
                            Toast.makeText(this@LoginActivity, "请输入正确手机号、用户名和至少6位密码", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        dismiss()
                        viewModel.register(phone, password, username)
                    }
                }
            }
            .show()
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
        }

        viewModel.registerResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = {
                    HomeActivity.start(this)
                    finish()
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
