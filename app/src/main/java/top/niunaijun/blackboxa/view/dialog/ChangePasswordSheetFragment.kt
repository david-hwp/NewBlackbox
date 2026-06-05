package top.niunaijun.blackboxa.view.dialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.databinding.BottomSheetChangePasswordBinding
import top.niunaijun.blackboxa.view.base.BaseBottomSheetFragment

class ChangePasswordSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetChangePasswordBinding? = null
    private val binding get() = _binding!!
    private var onConfirmListener: ((String, String, String) -> Unit)? = null

    fun setOnConfirmListener(listener: (String, String, String) -> Unit) {
        onConfirmListener = listener
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_change_password

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetChangePasswordBinding.bind(view)

        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnConfirm.setOnClickListener {
            if (validatePasswords()) {
                onConfirmListener?.invoke(
                    binding.etOldPassword.text.toString().trim(),
                    binding.etNewPassword.text.toString().trim(),
                    binding.etConfirmPassword.text.toString().trim()
                )
                dismissWithAnimation()
            }
        }

        // 实时校验确认密码
        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePasswords(showError = false)
            }
        })
    }

    private fun validatePasswords(showError: Boolean = true): Boolean {
        val oldPassword = binding.etOldPassword.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        when {
            oldPassword.isEmpty() -> {
                if (showError) {
                    binding.tvError.text = getString(R.string.error_password_empty)
                    binding.tvError.visibility = View.VISIBLE
                }
                return false
            }
            newPassword.isEmpty() -> {
                if (showError) {
                    binding.tvError.text = getString(R.string.error_password_empty)
                    binding.tvError.visibility = View.VISIBLE
                }
                return false
            }
            newPassword.length < 6 -> {
                if (showError) {
                    binding.tvError.text = getString(R.string.error_password_length)
                    binding.tvError.visibility = View.VISIBLE
                }
                return false
            }
            newPassword != confirmPassword -> {
                if (showError) {
                    binding.tvError.text = getString(R.string.error_password_mismatch)
                    binding.tvError.visibility = View.VISIBLE
                }
                return false
            }
            else -> {
                binding.tvError.visibility = View.GONE
                return true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
