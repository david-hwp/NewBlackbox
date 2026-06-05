package top.niunaijun.blackboxa.view.dialog

import android.os.Bundle
import android.view.View
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.BottomSheetDeleteShopBinding
import top.niunaijun.blackboxa.view.base.BaseBottomSheetFragment

class DeleteShopSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetDeleteShopBinding? = null
    private val binding get() = _binding!!

    private var onDeleteListener: (() -> Unit)? = null

    fun setOnDeleteListener(listener: () -> Unit) {
        onDeleteListener = listener
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_delete_shop

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetDeleteShopBinding.bind(view)

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnDelete.setOnClickListener {
            onDeleteListener?.invoke()
            dismissWithAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
