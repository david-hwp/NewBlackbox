package top.niunaijun.blackboxa.view.dialog

import android.os.Bundle
import android.view.View
import android.widget.Toast
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.BottomSheetEditShopBinding
import top.niunaijun.blackboxa.view.base.BaseBottomSheetFragment

class EditShopSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetEditShopBinding? = null
    private val binding get() = _binding!!

    private var currentShopName: String = ""
    private var currentAutoRenew: Boolean = false
    private var onSaveListener: ((String, Boolean) -> Unit)? = null

    fun setOnSaveListener(listener: (String, Boolean) -> Unit) {
        onSaveListener = listener
    }

    companion object {
        private const val ARG_SHOP_NAME = "shop_name"
        private const val ARG_AUTO_RENEW = "auto_renew"

        fun newInstance(shopName: String, autoRenew: Boolean): EditShopSheetFragment {
            return EditShopSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SHOP_NAME, shopName)
                    putBoolean(ARG_AUTO_RENEW, autoRenew)
                }
            }
        }
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_edit_shop

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetEditShopBinding.bind(view)

        currentShopName = arguments?.getString(ARG_SHOP_NAME) ?: ""
        currentAutoRenew = arguments?.getBoolean(ARG_AUTO_RENEW) ?: false

        binding.etShopName.setText(currentShopName)
        binding.switchAutoRenew.isChecked = currentAutoRenew

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnSave.setOnClickListener {
            val newName = binding.etShopName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "店铺名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSaveListener?.invoke(newName, binding.switchAutoRenew.isChecked)
            dismissWithAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
