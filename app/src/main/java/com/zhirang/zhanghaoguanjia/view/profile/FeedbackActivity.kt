package com.zhirang.zhanghaoguanjia.view.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zhirang.zhanghaoguanjia.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zhirang.zhanghaoguanjia.data.FeedbackRepository
import com.zhirang.zhanghaoguanjia.databinding.ActivityFeedbackBinding
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import com.zhirang.zhanghaoguanjia.util.SendLogsCollector

class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private val repository = FeedbackRepository(RetrofitClient.apiService)
    private val uploadedImages = mutableListOf<UploadedFeedbackImage>()
    private var isUploadingImages = false

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleSelectedImages(parseSelectedImageUris(result.data))
        }
    }

    companion object {
        private const val MAX_IMAGES = 5

        fun start(context: Context) {
            context.startActivity(Intent(context, FeedbackActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Duodian)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnAddImages.setOnClickListener { openImagePicker() }
        binding.btnSubmitFeedback.setOnClickListener { submit() }
        updateImageSummary()
    }

    private fun submit() {
        val content = binding.etFeedback.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入反馈内容", Toast.LENGTH_SHORT).show()
            return
        }
        if (isUploadingImages) {
            Toast.makeText(this, "图片正在上传，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        if (uploadedImages.size > MAX_IMAGES) {
            Toast.makeText(this, "最多上传5张图片", Toast.LENGTH_SHORT).show()
            return
        }

        setSubmitting(true)
        lifecycleScope.launch {
            var logCaption: String? = null
            var deviceInfo: String? = null
            val logUrl = if (binding.cbUploadLogs.isChecked) {
                val logBundle = withContext(Dispatchers.IO) {
                    SendLogsCollector.createLogZip(
                        this@FeedbackActivity,
                        "Feedback: ${content.take(120)}"
                    )
                }
                logCaption = logBundle.caption
                deviceInfo = logBundle.deviceInfo
                val logResult = withContext(Dispatchers.IO) {
                    try {
                        repository.uploadFeedbackLog(logBundle.zipFile)
                    } finally {
                        logBundle.zipFile.delete()
                    }
                }
                if (logResult.isFailure) {
                    setSubmitting(false)
                    Toast.makeText(
                        this@FeedbackActivity,
                        logResult.exceptionOrNull()?.message ?: "日志上传失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                logResult.getOrNull()
            } else {
                null
            }

            val imageUrls = uploadedImages.map { it.url }
            val result = repository.submitFeedback(content, imageUrls, logUrl, logCaption, deviceInfo)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@FeedbackActivity, "提交成功", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = {
                    setSubmitting(false)
                    Toast.makeText(this@FeedbackActivity, it.message ?: "提交失败", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun openImagePicker() {
        if (isUploadingImages) {
            Toast.makeText(this, "图片正在上传，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        if (uploadedImages.size >= MAX_IMAGES) {
            Toast.makeText(this, "最多上传5张图片", Toast.LENGTH_SHORT).show()
            return
        }
        imagePicker.launch(createImagePickerIntent(MAX_IMAGES - uploadedImages.size))
    }

    private fun createImagePickerIntent(maxSelection: Int): Intent {
        val pickFromSystemGallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra("pick-upper-bound", maxSelection)
            putExtra("pick-lower-bound", 1)
            putExtra("com.miui.gallery.extra.pick-upper-bound", maxSelection)
            putExtra("com.miui.gallery.extra.pick-lower-bound", 1)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (pickFromSystemGallery.resolveActivity(packageManager) != null) {
            return pickFromSystemGallery
        }

        return Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun parseSelectedImageUris(data: Intent?): List<Uri> {
        if (data == null) {
            return emptyList()
        }
        val result = mutableListOf<Uri>()
        data.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let(result::add)
            }
        }
        data.data?.let(result::add)
        return result.distinct()
    }

    private fun handleSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }
        val remaining = MAX_IMAGES - uploadedImages.size
        if (uris.size > remaining) {
            Toast.makeText(this, "最多上传5张图片", Toast.LENGTH_SHORT).show()
            return
        }
        uploadSelectedImages(uris)
    }

    private fun uploadSelectedImages(uris: List<Uri>) {
        isUploadingImages = true
        binding.btnAddImages.isEnabled = false
        binding.btnSubmitFeedback.isEnabled = false
        lifecycleScope.launch {
            uris.forEachIndexed { index, uri ->
                binding.tvImageSummary.text = "正在上传图片 ${index + 1}/${uris.size}"
                val result = withContext(Dispatchers.IO) { repository.uploadFeedbackImage(uri) }
                if (result.isFailure) {
                    Toast.makeText(
                        this@FeedbackActivity,
                        result.exceptionOrNull()?.message ?: "图片上传失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    isUploadingImages = false
                    binding.btnAddImages.isEnabled = true
                    binding.btnSubmitFeedback.isEnabled = true
                    updateImageSummary()
                    return@launch
                }
                uploadedImages += UploadedFeedbackImage(uri, result.getOrThrow())
                renderImagePreviews()
                updateImageSummary()
            }
            isUploadingImages = false
            binding.btnAddImages.isEnabled = true
            binding.btnSubmitFeedback.isEnabled = true
            updateImageSummary()
        }
    }

    private fun renderImagePreviews() {
        binding.imagePreviewList.removeAllViews()
        uploadedImages.forEachIndexed { index, item ->
            binding.imagePreviewList.addView(createImagePreview(item.uri, index))
        }
        binding.imagePreviewScroll.visibility = if (uploadedImages.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun createImagePreview(uri: Uri, index: Int): View {
        val size = dp(76)
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(8)
            }
        }
        val image = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(uri)
        }
        val delete = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END)
            setImageResource(R.drawable.ic_delete)
            setBackgroundResource(R.drawable.bg_card_white)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            setOnClickListener {
                if (index in uploadedImages.indices) {
                    uploadedImages.removeAt(index)
                    renderImagePreviews()
                    updateImageSummary()
                }
            }
        }
        frame.addView(image)
        frame.addView(delete)
        return frame
    }

    private fun updateImageSummary() {
        binding.tvImageSummary.text = if (uploadedImages.isEmpty()) {
            "未上传图片"
        } else {
            "已上传 ${uploadedImages.size}/$MAX_IMAGES 张图片"
        }
    }

    private fun setSubmitting(submitting: Boolean) {
        binding.btnSubmitFeedback.isEnabled = !submitting
        binding.btnAddImages.isEnabled = !submitting && !isUploadingImages
        binding.cbUploadLogs.isEnabled = !submitting
        binding.btnSubmitFeedback.text = if (submitting) "提交中..." else getString(R.string.feedback_submit)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class UploadedFeedbackImage(
        val uri: Uri,
        val url: String
    )
}
