package top.niunaijun.blackboxa.view.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.data.FeedbackRepository
import top.niunaijun.blackboxa.databinding.ActivityFeedbackBinding
import top.niunaijun.blackboxa.network.RetrofitClient
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private val repository = FeedbackRepository(RetrofitClient.apiService)
    private val imageUris = mutableListOf<Uri>()
    private val attachmentUris = mutableListOf<Uri>()

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        imageUris.clear()
        imageUris.addAll(uris.take(4))
        binding.tvImageSummary.text = if (imageUris.isEmpty()) "未选择图片" else "已选择 ${imageUris.size} 张图片"
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        attachmentUris.clear()
        uri?.let { attachmentUris.add(it) }
        binding.tvFileSummary.text = if (attachmentUris.isEmpty()) "未选择附件" else "已选择 ${attachmentUris.size} 个附件"
    }

    companion object {
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
        binding.btnAddImages.setOnClickListener { imagePicker.launch("image/*") }
        binding.btnAddFile.setOnClickListener { filePicker.launch("*/*") }
        binding.btnSubmitFeedback.setOnClickListener { submit() }
    }

    private fun submit() {
        val content = binding.etFeedback.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入反馈内容", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmitFeedback.isEnabled = false
        lifecycleScope.launch {
            val logZip = withContext(Dispatchers.IO) { createLogZip() }
            val result = repository.submitFeedback(content, imageUris, attachmentUris, logZip)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@FeedbackActivity, "提交成功", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = {
                    binding.btnSubmitFeedback.isEnabled = true
                    Toast.makeText(this@FeedbackActivity, it.message ?: "提交失败", Toast.LENGTH_SHORT).show()
                }
            )
        }
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
            val process = ProcessBuilder("logcat", "-d", "-T", "1d").redirectErrorStream(true).start()
            process.inputStream.use { input -> input.copyTo(zip) }
            process.destroy()
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
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
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
}
