package com.zhirang.zhanghaoguanjia.view.dialog

import android.os.Bundle
import android.view.View
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.databinding.BottomSheetAdvancedFeatureBinding
import com.zhirang.zhanghaoguanjia.view.base.BaseBottomSheetFragment

class AdvancedFeatureSheetFragment : BaseBottomSheetFragment() {
    private var _binding: BottomSheetAdvancedFeatureBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_CODE = "code"

        fun newInstance(title: String, code: String): AdvancedFeatureSheetFragment {
            return AdvancedFeatureSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_CODE, code)
                }
            }
        }
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_advanced_feature

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetAdvancedFeatureBinding.bind(view)
        binding.tvAdvancedFeatureTitle.text = arguments?.getString(ARG_TITLE).orEmpty()
        binding.tvAdvancedFeaturePlaceholder.text = getString(R.string.advanced_feature_developing)
        binding.btnClose.setOnClickListener {
            dismissWithAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
