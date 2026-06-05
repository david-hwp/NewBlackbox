package top.niunaijun.blackboxa.view.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import top.niunaijun.blackboxa.data.TokenManager
import top.niunaijun.blackboxa.databinding.ActivityLoginBinding
import top.niunaijun.blackboxa.view.home.HomeActivity

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
        binding.btnLogin.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入手机号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(phone, password)
        }
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
            binding.btnLogin.text = if (isLoading) "登录中…" else "登录"
        }

        viewModel.errorLiveData.observe(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
