package com.zhirang.zhanghaoguanjia.view.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private val repository = FeedbackRepository(RetrofitClient.apiService)
    private val uploadedImages = mutableListOf<UploadedFeedbackImage>()
    private var isUploadingImages = false

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
    ) { uris ->
        handleSelectedImages(uris)
    }

    companion object {
        private const val MAX_IMAGES = 5
        private const val LOG_WINDOW_MS = 60 * 60 * 1000L
        private const val LOGCAT_TIMEOUT_SECONDS = 8L
        private const val MAX_LOGCAT_LINES = 5000
        private val LOGCAT_TIMESTAMP_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

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
            val logUrl = if (binding.cbUploadLogs.isChecked) {
                val logZip = withContext(Dispatchers.IO) { createLogZip() }
                val logResult = withContext(Dispatchers.IO) { repository.uploadFeedbackLog(logZip) }
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
            val result = repository.submitFeedback(content, imageUrls, logUrl)
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
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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

    private fun createLogZip(): File {
        val zipFile = File(cacheDir, "feedback-logs-${System.currentTimeMillis()}.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            addLogcat(zip)
            collectLogDirectories().forEach { (dir, prefix) ->
                addRecentFiles(zip, dir, prefix)
            }
        }
        return zipFile
    }

    private fun addLogcat(zip: ZipOutputStream) {
        val entry = ZipEntry("logcat.txt")
        zip.putNextEntry(entry)
        runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "*:I")
                .redirectErrorStream(true)
                .start()
            val cutoff = System.currentTimeMillis() - LOG_WINDOW_MS
            var wroteAnyLine = false
            var keptLines = 0
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (isRecentLogcatLine(line, cutoff)) {
                        zip.write(line.toByteArray())
                        zip.write('\n'.code)
                        wroteAnyLine = true
                        keptLines += 1
                    }
                    if (keptLines >= MAX_LOGCAT_LINES) {
                        return@useLines
                    }
                }
            }
            if (!process.waitFor(LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
            }
            if (!wroteAnyLine) {
                zip.write("No INFO+ logcat lines found in the last hour.\n".toByteArray())
            }
        }.onFailure {
            zip.write("logcat unavailable: ${it.message}".toByteArray())
        }
        zip.closeEntry()
    }

    private fun collectLogDirectories(): List<Pair<File, String>> {
        val dirs = mutableListOf<Pair<File, String>>()
        dirs += filesDir to "apk-files"
        dirs += cacheDir to "apk-cache"
        filesDir.parentFile?.let { dirs += it to "apk-private" }
        getExternalFilesDir(null)?.let { dirs += it to "apk-external-files" }
        externalCacheDir?.let { dirs += it to "apk-external-cache" }
        return dirs.distinctBy { it.first.absolutePath }
    }

    private fun addRecentFiles(zip: ZipOutputStream, dir: File, prefix: String) {
        if (!dir.exists() || !dir.canRead()) return
        val cutoff = System.currentTimeMillis() - LOG_WINDOW_MS
        dir.walkTopDown()
            .onEnter { it.canRead() }
            .filter { file ->
                file.isFile &&
                    file.canRead() &&
                    file.lastModified() >= cutoff &&
                    (file.extension == "log" || file.extension == "txt")
            }
            .take(60)
            .forEach { file ->
                val entryName = "$prefix/${file.relativeTo(dir).path}"
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }

    private fun isRecentLogcatLine(line: String, cutoff: Long): Boolean {
        if (line.length < 18) {
            return false
        }
        val timestamp = line.substring(0, 18)
        val parsed = runCatching {
            synchronized(LOGCAT_TIMESTAMP_FORMAT) {
                LOGCAT_TIMESTAMP_FORMAT.parse(timestamp)
            }
        }.getOrNull() ?: return false
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
        }
        if (calendar.timeInMillis - now > TimeUnit.DAYS.toMillis(1)) {
            calendar.add(Calendar.YEAR, -1)
        }
        return calendar.timeInMillis >= cutoff
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class UploadedFeedbackImage(
        val uri: Uri,
        val url: String
    )
}
