package top.niunaijun.blackbox.engine

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.Slog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EngineCloneDataExportActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var outputView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        outputView = TextView(this).apply {
            textSize = 12f
            setPadding(32, 32, 32, 32)
            gravity = Gravity.START
            text = "Exporting clone data..."
        }
        setContentView(ScrollView(this).apply { addView(outputView) })

        Thread {
            val result = runCatching { exportCloneData() }
                .getOrElse { error ->
                    JSONObject()
                        .put("ok", false)
                        .put("error", error.message ?: error.javaClass.name)
                }
            val pretty = result.toString(2)
            Slog.d(TAG, "Clone data export result: $pretty")
            handler.post { outputView.text = pretty }
        }.start()
    }

    private fun exportCloneData(): JSONObject {
        BEnvironment.load()
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val userId = intent.getIntExtra(EXTRA_USER_ID, 0)
        val includeAll = intent.getBooleanExtra(EXTRA_INCLUDE_ALL, true)

        val outDir = getExternalFilesDir("clone-export") ?: File(filesDir, "clone-export")
        outDir.mkdirs()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safePackage = packageName.ifBlank { "all" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val zipFile = File(outDir, "clone_data_${safePackage}_u${userId}_$stamp.zip")
        val tmpFile = File(outDir, zipFile.name + ".tmp")
        val manifestFile = File(outDir, "clone_data_${safePackage}_u${userId}_$stamp.json")

        val exporter = CloneDataZipExporter(tmpFile)
        val roots = JSONArray()
        ZipOutputStream(FileOutputStream(tmpFile).buffered()).use { zip ->
            if (includeAll) {
                roots.put(exporter.addRoot(zip, BEnvironment.getVirtualRoot(), "virtual-root"))
                val externalVirtualRoot = BEnvironment.getExternalVirtualRoot()
                roots.put(exporter.addRoot(zip, externalVirtualRoot, "external-virtual-root"))
            } else if (packageName.isNotBlank()) {
                roots.put(exporter.addRoot(zip, BEnvironment.getDataDir(packageName, userId), "virtual-data-user-$userId/$packageName"))
                roots.put(exporter.addRoot(zip, BEnvironment.getDeDataDir(packageName, userId), "virtual-data-user-de-$userId/$packageName"))
                roots.put(exporter.addRoot(zip, BEnvironment.getExternalDataDir(packageName, userId), "external-data-user-$userId/$packageName"))
            } else {
                // Nothing to export for a package-scoped request without packageName.
            }
        }
        if (zipFile.exists()) zipFile.delete()
        if (!tmpFile.renameTo(zipFile)) {
            tmpFile.copyTo(zipFile, overwrite = true)
            tmpFile.delete()
        }

        val target = JSONObject()
            .put("packageName", packageName)
            .put("userId", userId)
            .put("dataDir", describe(BEnvironment.getDataDir(packageName, userId)))
            .put("deDataDir", describe(BEnvironment.getDeDataDir(packageName, userId)))
            .put("externalDataDir", describe(BEnvironment.getExternalDataDir(packageName, userId)))

        val result = JSONObject()
            .put("ok", true)
            .put("generatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .put("includeAll", includeAll)
            .put("zipFile", zipFile.absolutePath)
            .put("zipBytes", zipFile.length())
            .put("manifestFile", manifestFile.absolutePath)
            .put("target", target)
            .put("roots", roots)
            .put("stats", exporter.stats())
            .put("errors", exporter.errors)

        manifestFile.writeText(result.toString(2), Charsets.UTF_8)
        return result
    }

    private fun describe(file: File): JSONObject {
        return JSONObject()
            .put("path", file.absolutePath)
            .put("exists", file.exists())
            .put("isDirectory", file.isDirectory)
            .put("bytes", file.takeIf { it.exists() }?.let { measureBytes(it) } ?: 0L)
    }

    private fun measureBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var bytes = 0L
        file.walkTopDown().forEach {
            if (it.isFile) bytes += it.length()
        }
        return bytes
    }

    companion object {
        private const val TAG = "EngineCloneExport"
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val EXTRA_USER_ID = "userId"
        private const val EXTRA_INCLUDE_ALL = "includeAll"
    }
}

private class CloneDataZipExporter(private val outputFile: File) {
    val errors = JSONArray()
    private var fileCount = 0
    private var dirCount = 0
    private var bytes = 0L
    private var skipped = 0
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    private val outputCanonicalPath = outputFile.canonicalPath

    fun addRoot(zip: ZipOutputStream, root: File, zipPrefix: String): JSONObject {
        val beforeFiles = fileCount
        val beforeDirs = dirCount
        val beforeBytes = bytes
        val exists = root.exists()
        if (exists) {
            addFile(zip, root, zipPrefix.trim('/'))
        }
        return JSONObject()
            .put("name", zipPrefix)
            .put("path", root.absolutePath)
            .put("exists", exists)
            .put("files", fileCount - beforeFiles)
            .put("dirs", dirCount - beforeDirs)
            .put("bytes", bytes - beforeBytes)
    }

    fun stats(): JSONObject {
        return JSONObject()
            .put("files", fileCount)
            .put("dirs", dirCount)
            .put("bytes", bytes)
            .put("skipped", skipped)
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        if (file.canonicalPath == outputCanonicalPath) {
            skipped++
            return
        }
        if (file.isDirectory) {
            dirCount++
            val normalizedDir = entryName.trimEnd('/') + "/"
            zip.putNextEntry(ZipEntry(normalizedDir).apply {
                time = file.lastModified()
            })
            zip.closeEntry()
            file.listFiles()?.sortedBy { it.name }?.forEach { child ->
                addFile(zip, child, "$normalizedDir${child.name}")
            }
            return
        }
        if (!file.isFile) {
            skipped++
            return
        }
        runCatching {
            zip.putNextEntry(ZipEntry(entryName).apply {
                time = file.lastModified()
            })
            BufferedInputStream(FileInputStream(file)).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zip.write(buffer, 0, read)
                    bytes += read
                }
            }
            zip.closeEntry()
            fileCount++
        }.onFailure { error ->
            skipped++
            runCatching { zip.closeEntry() }
            errors.put(
                JSONObject()
                    .put("path", file.absolutePath)
                    .put("error", error.message ?: error.javaClass.name)
            )
        }
    }
}
