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
    private val phoneMinutesMode: Boolean
        get() = intent.getBooleanExtra(EXTRA_PHONE_MINUTES_MODE, false)

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, GiftActivity::class.java))
        }

        fun startPhoneMinutes(context: Context) {
            context.startActivity(Intent(context, GiftActivity::class.java).putExtra(EXTRA_PHONE_MINUTES_MODE, true))
        }

        private const val EXTRA_PHONE_MINUTES_MODE = "phone_minutes_mode"
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
        binding.toolbar.title = getString(if (phoneMinutesMode) R.string.phone_gift_title else R.string.gift_title)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initMockData() {
        val user = TokenManager.getInstance().getUser()
        binding.tvPhone.text = maskPhone(user?.phone.orEmpty())
        binding.tvBalance.text = getTransferableBalance().toString()
        binding.tvBalanceLabel.text = getString(if (phoneMinutesMode) R.string.phone_gift_balance_label else R.string.compute_gift_balance_label)
        binding.tvAmountLabel.text = getString(if (phoneMinutesMode) R.string.phone_gift_amount else R.string.gift_amount)
        binding.etAmount.hint = getString(if (phoneMinutesMode) R.string.phone_gift_amount_hint else R.string.compute_gift_amount_hint)
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
        val parsedAmount = amount.toIntOrNull()
        if (amount.isEmpty() || parsedAmount == null || parsedAmount <= 0) {
            binding.tvAmountError.visibility = View.VISIBLE
            binding.tvAmountError.text = getString(R.string.error_amount_invalid)
            isValid = false
        } else if (parsedAmount > getTransferableBalance()) {
            binding.tvAmountError.visibility = View.VISIBLE
            binding.tvAmountError.text = getString(if (phoneMinutesMode) R.string.error_phone_minutes_balance_insufficient else R.string.error_compute_transferable_insufficient)
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
            amountText = "$amount ${unitText()}"
        )
        sheet.setOnConfirmListener {
            viewModel.gift(phone, amount, phoneMinutesMode)
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
            val updated = if (phoneMinutesMode) {
                user.copy(phoneMinutesBalance = result.fromBalance)
            } else {
                user.copy(computeBalance = result.fromBalance)
            }
            TokenManager.getInstance().saveUser(updated)
        }
        binding.tvBalance.text = getTransferableBalance().toString()
        binding.btnConfirm.text = getString(R.string.gift_success)
        binding.btnConfirm.setBackgroundColor(getColor(R.color.duodian_primary))

        Handler(Looper.getMainLooper()).postDelayed({
            binding.etTargetPhone.text.clear()
            binding.etAmount.text.clear()
            binding.btnConfirm.text = getString(R.string.gift_confirm)
            binding.btnConfirm.setBackgroundResource(R.drawable.bg_button_primary)

            binding.tvBalance.text = getTransferableBalance().toString()
        }, 1500)
    }

    private fun getTransferableBalance(): Int {
        val user = TokenManager.getInstance().getUser() ?: return 0
        if (phoneMinutesMode) {
            return user.phoneMinutesBalance.coerceAtLeast(0)
        }
        return (user.computeBalance - user.nonTransferableComputeBalance).coerceAtLeast(0)
    }

    private fun unitText(): String = getString(if (phoneMinutesMode) R.string.phone_minutes_unit else R.string.gift_confirm_unit)

    private fun maskPhone(phone: String): String {
        return if (phone.length == 11) {
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        } else {
            phone
        }
    }
}
