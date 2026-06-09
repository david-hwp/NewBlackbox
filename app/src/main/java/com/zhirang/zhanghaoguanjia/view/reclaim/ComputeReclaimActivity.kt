package com.zhirang.zhanghaoguanjia.view.reclaim

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.bean.dto.ComputeReclaimResult
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.databinding.ActivityComputeReclaimBinding
import com.zhirang.zhanghaoguanjia.view.dialog.ComputeReclaimSheetFragment
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ComputeReclaimActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComputeReclaimBinding
    private lateinit var viewModel: ComputeReclaimViewModel
    private var latestResult: ComputeReclaimResult? = null

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ComputeReclaimActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Duodian)
        binding = ActivityComputeReclaimBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ComputeReclaimViewModel::class.java]

        initToolbar()
        initListeners()
        observeViewModel()
        renderResult(null)
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initListeners() {
        binding.btnQuery.setOnClickListener {
            val phone = binding.etTargetPhone.text.toString().trim()
            if (validatePhone(phone)) {
                viewModel.query(phone)
            }
        }

        binding.btnReclaim.setOnClickListener {
            val result = latestResult ?: return@setOnClickListener
            if (result.reclaimableAmount <= 0) {
                Toast.makeText(this, getString(R.string.reclaim_none_available), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showReclaimSheet(result)
        }
    }

    private fun observeViewModel() {
        viewModel.queryResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = {
                    latestResult = it
                    renderResult(it)
                },
                onFailure = {
                    latestResult = null
                    renderResult(null)
                }
            )
        }

        viewModel.reclaimResultLiveData.observe(this) { result ->
            result?.fold(
                onSuccess = { onReclaimSuccess(it) },
                onFailure = {}
            )
        }

        viewModel.errorLiveData.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.loadingLiveData.observe(this) { isLoading ->
            val enabled = !isLoading
            binding.btnQuery.isEnabled = enabled
            binding.btnReclaim.isEnabled = enabled && (latestResult?.reclaimableAmount ?: 0) > 0
            binding.btnQuery.text = if (isLoading) getString(R.string.querying) else getString(R.string.query)
        }
    }

    private fun onReclaimSuccess(result: ComputeReclaimResult) {
        latestResult = result
        renderResult(result)
        result.fromBalance?.let { balance ->
            TokenManager.getInstance().getUser()?.let { user ->
                TokenManager.getInstance().saveUser(user.copy(computeBalance = balance))
            }
        }
        val amount = result.reclaimedAmount ?: 0
        Toast.makeText(this, getString(R.string.reclaim_success_with_amount, amount), Toast.LENGTH_SHORT).show()
    }

    private fun showReclaimSheet(result: ComputeReclaimResult) {
        val sheet = ComputeReclaimSheetFragment.newInstance(result.reclaimableAmount)
        sheet.setOnConfirmListener { amount ->
            viewModel.reclaim(result.toPhone, result.giftLogId, amount)
        }
        sheet.show(supportFragmentManager, "ComputeReclaim")
    }

    private fun validatePhone(phone: String): Boolean {
        return if (phone.isEmpty() || phone.length != 11 || !phone.matches(Regex("^1[3-9]\\d{9}$"))) {
            binding.tvPhoneError.text = getString(R.string.error_phone_invalid)
            binding.tvPhoneError.visibility = View.VISIBLE
            false
        } else {
            binding.tvPhoneError.visibility = View.GONE
            true
        }
    }

    private fun renderResult(result: ComputeReclaimResult?) {
        if (result == null) {
            binding.receiptCard.visibility = View.GONE
            binding.btnReclaim.isEnabled = false
            return
        }

        binding.receiptCard.visibility = View.VISIBLE
        binding.tvReceiptPhone.text = maskPhone(result.toPhone)
        binding.tvReceiptTime.text = formatTime(result.giftCreatedAt)
        binding.tvGiftAmount.text = getString(R.string.reclaim_compute_unit, result.giftAmount)
        binding.tvConsumedAmount.text = getString(R.string.reclaim_compute_unit, result.receiverConsumedAmount)
        binding.tvAlreadyReclaimedAmount.text = getString(R.string.reclaim_compute_unit, result.alreadyReclaimedAmount)
        binding.tvReclaimableAmount.text = getString(R.string.reclaim_compute_unit, result.reclaimableAmount)
        binding.btnReclaim.isEnabled = result.reclaimableAmount > 0
    }

    private fun formatTime(value: String?): String {
        if (value.isNullOrBlank()) {
            return "-"
        }
        return runCatching {
            val normalized = value.substringBefore(".")
            LocalDateTime.parse(normalized).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrElse { value.replace('T', ' ') }
    }

    private fun maskPhone(phone: String): String {
        return if (phone.length == 11) {
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        } else {
            phone
        }
    }
}
