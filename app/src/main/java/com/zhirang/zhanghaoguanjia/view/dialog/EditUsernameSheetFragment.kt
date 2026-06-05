package com.zhirang.zhanghaoguanjia.view.dialog

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.databinding.BottomSheetEditUsernameBinding
import com.zhirang.zhanghaoguanjia.util.AvatarImageLoader
import com.zhirang.zhanghaoguanjia.view.base.BaseBottomSheetFragment

class EditUsernameSheetFragment : BaseBottomSheetFragment() {

    private var _binding: BottomSheetEditUsernameBinding? = null
    private val binding get() = _binding!!

    private var currentUsername: String = ""
    private var currentAvatarUrl: String? = null
    private var selectedAvatarUri: Uri? = null
    private var onSaveListener: ((String, Uri?, String?) -> Unit)? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        selectedAvatarUri = uri
        binding.ivAvatarPreview.setImageURI(uri)
        binding.ivAvatarPreview.visibility = View.VISIBLE
        binding.tvAvatarPreviewInitial.visibility = View.GONE
    }

    fun setOnSaveListener(listener: (String, Uri?, String?) -> Unit) {
        onSaveListener = listener
    }

    companion object {
        private const val ARG_USERNAME = "username"
        private const val ARG_AVATAR_URL = "avatarUrl"

        fun newInstance(username: String, avatarUrl: String?): EditUsernameSheetFragment {
            return EditUsernameSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                    putString(ARG_AVATAR_URL, avatarUrl)
                }
            }
        }
    }

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_edit_username

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = BottomSheetEditUsernameBinding.bind(view)

        currentUsername = arguments?.getString(ARG_USERNAME) ?: ""
        currentAvatarUrl = arguments?.getString(ARG_AVATAR_URL)
        binding.etUsername.setText(currentUsername)
        updateAvatarPreview(currentUsername, currentAvatarUrl)

        binding.avatarPicker.setOnClickListener {
            avatarPicker.launch("image/*")
        }

        binding.btnChangeAvatar.setOnClickListener {
            avatarPicker.launch("image/*")
        }

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
                    onSaveListener?.invoke(username, selectedAvatarUri, currentAvatarUrl)
                    dismissWithAnimation()
                }
            }
        }
    }

    private fun updateAvatarPreview(username: String, avatarUrl: String?) {
        AvatarImageLoader.bind(
            imageView = binding.ivAvatarPreview,
            fallbackView = binding.tvAvatarPreviewInitial,
            avatarUrl = avatarUrl,
            initial = username
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
