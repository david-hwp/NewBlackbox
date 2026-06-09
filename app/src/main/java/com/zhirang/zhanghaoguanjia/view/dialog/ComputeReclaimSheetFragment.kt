package com.zhirang.zhanghaoguanjia.view.dialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.databinding.BottomSheetComputeReclaimBinding
import com.zhirang.zhanghaoguanjia.view.base.BaseBottomSheetFragment

class ComputeReclaimSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetComputeReclaimBinding? = null
    private val binding get() = _binding!!
    private var maxAmount: Int = 0
    private var onConfirmListener: ((Int) -> Unit)? = null

    fun setOnConfirmListener(listener: (Int) -> Unit) {
        onConfirmListener = listener
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_compute_reclaim

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetComputeReclaimBinding.bind(view)

        maxAmount = arguments?.getInt(ARG_MAX_AMOUNT) ?: 0
        binding.tvMaxAmount.text = getString(R.string.reclaim_dialog_max_amount, maxAmount)
        binding.etAmount.setText(maxAmount.toString())
        binding.etAmount.setSelection(binding.etAmount.text?.length ?: 0)

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.tvAmountError.visibility = View.GONE
            }
        })

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnConfirm.setOnClickListener {
            val amount = binding.etAmount.text.toString().trim().toIntOrNull()
            when {
                amount == null || amount <= 0 -> showError(getString(R.string.error_reclaim_amount_invalid))
                amount > maxAmount -> showError(getString(R.string.error_reclaim_amount_exceeds))
                else -> {
                    onConfirmListener?.invoke(amount)
                    dismissWithAnimation()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showError(message: String) {
        binding.tvAmountError.text = message
        binding.tvAmountError.visibility = View.VISIBLE
    }

    companion object {
        private const val ARG_MAX_AMOUNT = "max_amount"

        fun newInstance(maxAmount: Int): ComputeReclaimSheetFragment {
            return ComputeReclaimSheetFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MAX_AMOUNT, maxAmount)
                }
            }
        }
    }
}
