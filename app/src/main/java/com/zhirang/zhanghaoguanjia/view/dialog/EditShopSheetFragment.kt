package com.zhirang.zhanghaoguanjia.view.dialog

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.databinding.BottomSheetEditShopBinding
import com.zhirang.zhanghaoguanjia.view.base.BaseBottomSheetFragment

class EditShopSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetEditShopBinding? = null
    private val binding get() = _binding!!

    private var currentShopName: String = ""
    private var currentShopId: String = ""
    private var currentAutoRenew: Boolean = false
    private var onSaveListener: ((String, String, Boolean) -> Unit)? = null

    fun setOnSaveListener(listener: (String, String, Boolean) -> Unit) {
        onSaveListener = listener
    }

    companion object {
        private const val ARG_SHOP_NAME = "shop_name"
        private const val ARG_SHOP_ID = "shop_id"
        private const val ARG_AUTO_RENEW = "auto_renew"

        fun newInstance(shopName: String, shopId: String, autoRenew: Boolean): EditShopSheetFragment {
            return EditShopSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SHOP_NAME, shopName)
                    putString(ARG_SHOP_ID, shopId)
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
        currentShopId = arguments?.getString(ARG_SHOP_ID) ?: ""
        currentAutoRenew = arguments?.getBoolean(ARG_AUTO_RENEW) ?: false

        binding.etShopName.setText(currentShopName)
        binding.etShopId.setText(currentShopId.takeUnless { it.startsWith("NEW-") }.orEmpty())
        binding.etShopName.isEnabled = false
        binding.etShopId.isEnabled = false
        binding.switchAutoRenew.isChecked = currentAutoRenew

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnSave.setOnClickListener {
            if (currentShopName.isEmpty()) {
                Toast.makeText(requireContext(), "店铺身份需由引擎识别", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSaveListener?.invoke(currentShopName, currentShopId, binding.switchAutoRenew.isChecked)
            dismissWithAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
