package com.zhirang.zhanghaoguanjia.util

import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SendLogsCollector {
    private const val LOGCAT_TIMEOUT_SECONDS = 12L

    data class LogBundle(
        val zipFile: File,
        val caption: String,
        val deviceInfo: String
    )

    fun createLogZip(context: Context, caption: String): LogBundle {
        val appContext = context.applicationContext
        val normalizedCaption = caption.ifBlank { "Feedback Log Upload" }
        val deviceInfo = buildDeviceInfo(appContext)
        val rawLog = File.createTempFile("send_logs_", ".txt", appContext.cacheDir)
        val zipFile = File(appContext.cacheDir, "feedback-logs-${System.currentTimeMillis()}.zip")

        try {
            rawLog.outputStream().use { output ->
                output.write("Caption: $normalizedCaption\n\n".toByteArray(Charsets.UTF_8))
                output.write(deviceInfo.toByteArray(Charsets.UTF_8))
                output.write("\n\n--- LOGCAT ---\n".toByteArray(Charsets.UTF_8))
                appendLogcat(output)
            }
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("logcat.txt"))
                rawLog.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        } finally {
            rawLog.delete()
        }

        return LogBundle(zipFile, normalizedCaption, deviceInfo)
    }

    private fun appendLogcat(output: java.io.OutputStream) {
        runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime")
                .redirectErrorStream(true)
                .start()
            process.inputStream.use { it.copyTo(output) }
            if (!process.waitFor(LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                output.write("\nlogcat collection timed out.\n".toByteArray(Charsets.UTF_8))
            }
        }.onFailure {
            output.write("logcat unavailable: ${it.message}\n".toByteArray(Charsets.UTF_8))
        }
    }

    private fun buildDeviceInfo(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toString()
        }

        return buildString {
            appendLine("DEVICE INFORMATION")
            appendLine("------------------")
            appendLine("Android Version: ${Build.VERSION.RELEASE}")
            appendLine("SDK Level: ${Build.VERSION.SDK_INT}")
            appendLine("Build ID: ${Build.ID}")
            appendLine("Build Display: ${Build.DISPLAY}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appendLine("Security Patch: ${Build.VERSION.SECURITY_PATCH}")
            }
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Board: ${Build.BOARD}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty()) {
                appendLine("32-bit ABIs: ${Build.SUPPORTED_32_BIT_ABIS.joinToString(", ")}")
            }
            if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                appendLine("64-bit ABIs: ${Build.SUPPORTED_64_BIT_ABIS.joinToString(", ")}")
            }
            appendLine()
            appendLine("APP INFORMATION")
            appendLine("---------------")
            appendLine("Package: ${context.packageName}")
            appendLine("Version Name: ${packageInfo?.versionName.orEmpty()}")
            appendLine("Version Code: ${versionCode.orEmpty()}")
            appendLine("Locale: ${Locale.getDefault()}")
        }
    }
}
