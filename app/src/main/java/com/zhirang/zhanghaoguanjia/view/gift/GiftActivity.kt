package com.zhirang.zhanghaoguanjia.view.gift

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.bean.dto.GiftResult
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.databinding.ActivityGiftBinding
import com.zhirang.zhanghaoguanjia.view.dialog.GiftConfirmSheetFragment

class GiftActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGiftBinding
    private lateinit var viewModel: GiftViewModel

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, GiftActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Duodian)
        binding = ActivityGiftBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[GiftViewModel::class.java]

        initToolbar()
        initMockData()
        initListeners()
        observeViewModel()
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initMockData() {
        val user = TokenManager.getInstance().getUser()
        binding.tvPhone.text = maskPhone(user?.phone.orEmpty())
        binding.tvBalance.text = (user?.computeBalance ?: 0).toString()
    }

    private fun initListeners() {
        binding.btnConfirm.setOnClickListener {
            if (validateInput()) {
                showConfirmBottomSheet()
            }
        }
    }

    private fun validateInput(): Boolean {
        var isValid = true

        val phone = binding.etTargetPhone.text.toString().trim()
        if (phone.isEmpty() || phone.length != 11 || !phone.matches(Regex("^1[3-9]\\d{9}$"))) {
            binding.tvPhoneError.visibility = View.VISIBLE
            binding.tvPhoneError.text = getString(R.string.error_phone_invalid)
            isValid = false
        } else {
            binding.tvPhoneError.visibility = View.GONE
        }

        val amount = binding.etAmount.text.toString().trim()
        if (amount.isEmpty() || amount.toIntOrNull() == null || amount.toInt() <= 0) {
            binding.tvAmountError.visibility = View.VISIBLE
            binding.tvAmountError.text = getString(R.string.error_amount_invalid)
            isValid = false
        } else {
            binding.tvAmountError.visibility = View.GONE
        }

        return isValid
    }

    private fun showConfirmBottomSheet() {
        val phone = binding.etTargetPhone.text.toString().trim()
        val amount = binding.etAmount.text.toString().trim().toInt()

        val sheet = GiftConfirmSheetFragment.newInstance(
            targetPhone = maskPhone(phone),
            amountText = "$amount ${getString(R.string.gift_confirm_unit)}"
        )
        sheet.setOnConfirmListener {
            viewModel.gift(phone, amount)
        }
        sheet.show(supportFragmentManager, "GiftConfirm")
    }

    private fun observeViewModel() {
        viewModel.giftResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = { onGiftSuccess(it) },
                onFailure = {}
            )
        }

        viewModel.errorLiveData.observe(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }

        viewModel.loadingLiveData.observe(this) { isLoading ->
            binding.btnConfirm.isEnabled = !isLoading
        }
    }

    private fun onGiftSuccess(result: GiftResult) {
        TokenManager.getInstance().getUser()?.let { user ->
            TokenManager.getInstance().saveUser(user.copy(computeBalance = result.fromBalance))
        }
        binding.tvBalance.text = result.fromBalance.toString()
        binding.btnConfirm.text = getString(R.string.gift_success)
        binding.btnConfirm.setBackgroundColor(getColor(R.color.duodian_primary))

        Handler(Looper.getMainLooper()).postDelayed({
            binding.etTargetPhone.text.clear()
            binding.etAmount.text.clear()
            binding.btnConfirm.text = getString(R.string.gift_confirm)
            binding.btnConfirm.setBackgroundResource(R.drawable.bg_button_primary)

            binding.tvBalance.text = result.fromBalance.toString()
        }, 1500)
    }

    private fun maskPhone(phone: String): String {
        return if (phone.length == 11) {
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        } else {
            phone
        }
    }
}
