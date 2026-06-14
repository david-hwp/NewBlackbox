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
    private var currentRemark: String = ""
    private var onSaveListener: ((String, String, Boolean, String) -> Unit)? = null

    fun setOnSaveListener(listener: (String, String, Boolean, String) -> Unit) {
        onSaveListener = listener
    }

    companion object {
        private const val ARG_SHOP_NAME = "shop_name"
        private const val ARG_SHOP_ID = "shop_id"
        private const val ARG_AUTO_RENEW = "auto_renew"
        private const val ARG_REMARK = "remark"

        fun newInstance(shopName: String, shopId: String, autoRenew: Boolean, remark: String?): EditShopSheetFragment {
            return EditShopSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SHOP_NAME, shopName)
                    putString(ARG_SHOP_ID, shopId)
                    putBoolean(ARG_AUTO_RENEW, autoRenew)
                    putString(ARG_REMARK, remark.orEmpty())
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
        currentRemark = arguments?.getString(ARG_REMARK) ?: ""

        binding.etShopName.setText(currentShopName)
        binding.etShopId.setText(currentShopId.takeUnless { it.startsWith("NEW-") }.orEmpty())
        binding.switchAutoRenew.isChecked = currentAutoRenew
        binding.etRemark.setText(currentRemark)

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnSave.setOnClickListener {
            val nextShopName = binding.etShopName.text?.toString()?.trim().orEmpty()
            val nextShopId = binding.etShopId.text?.toString()?.trim().orEmpty()
            if (nextShopName.isEmpty()) {
                Toast.makeText(requireContext(), "请输入店铺名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSaveListener?.invoke(
                nextShopName,
                nextShopId.ifBlank { "-" },
                binding.switchAutoRenew.isChecked,
                binding.etRemark.text?.toString().orEmpty()
            )
            dismissWithAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
