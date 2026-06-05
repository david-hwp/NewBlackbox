package com.zhirang.zhanghaoguanjia.view.dialog

import android.os.Bundle
import android.view.View
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.databinding.BottomSheetEditUsernameBinding
import com.zhirang.zhanghaoguanjia.view.base.BaseBottomSheetFragment

class EditUsernameSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetEditUsernameBinding? = null
    private val binding get() = _binding!!

    private var currentUsername: String = ""
    private var onSaveListener: ((String) -> Unit)? = null

    fun setOnSaveListener(listener: (String) -> Unit) {
        onSaveListener = listener
    }

    companion object {
        private const val ARG_USERNAME = "username"

        fun newInstance(username: String): EditUsernameSheetFragment {
            return EditUsernameSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                }
            }
        }
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_edit_username

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetEditUsernameBinding.bind(view)

        currentUsername = arguments?.getString(ARG_USERNAME) ?: ""
        binding.etUsername.setText(currentUsername)

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnSave.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()

            when {
                username.isEmpty() -> {
                    binding.tvError.text = getString(R.string.error_username_empty)
                    binding.tvError.visibility = View.VISIBLE
                }
                username.length > 32 -> {
                    binding.tvError.text = getString(R.string.error_username_length)
                    binding.tvError.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvError.visibility = View.GONE
                    onSaveListener?.invoke(username)
                    dismissWithAnimation()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
