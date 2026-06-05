package top.niunaijun.blackboxa.view.dialog

import android.os.Bundle
import android.view.View
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.BottomSheetGiftConfirmBinding
import top.niunaijun.blackboxa.view.base.BaseBottomSheetFragment

class GiftConfirmSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetGiftConfirmBinding? = null
    private val binding get() = _binding!!
    private var onConfirmListener: (() -> Unit)? = null

    fun setOnConfirmListener(listener: () -> Unit) {
        onConfirmListener = listener
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_gift_confirm

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetGiftConfirmBinding.bind(view)

        binding.tvTargetPhone.text = arguments?.getString(ARG_TARGET_PHONE).orEmpty()
        binding.tvAmount.text = arguments?.getString(ARG_AMOUNT_TEXT).orEmpty()

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnConfirm.setOnClickListener {
            onConfirmListener?.invoke()
            dismissWithAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TARGET_PHONE = "target_phone"
        private const val ARG_AMOUNT_TEXT = "amount_text"

        fun newInstance(targetPhone: String, amountText: String): GiftConfirmSheetFragment {
            return GiftConfirmSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_PHONE, targetPhone)
                    putString(ARG_AMOUNT_TEXT, amountText)
                }
            }
        }
    }
}
