package com.zhirang.zhanghaoguanjia.update

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.zhirang.zhanghaoguanjia.bean.dto.AppVersionDto
import com.zhirang.zhanghaoguanjia.data.AppVersionRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val UPDATE_DIR = "app-update"

    private val repository = AppVersionRepository(RetrofitClient.apiService)
    private val httpClient = OkHttpClient()

    suspend fun checkForUpdate(context: Context): Result<AppVersionDto?> {
        val currentVersion = getCurrentVersionCode(context)
        return repository.getPublishedVersions().map { versions ->
            versions
                .filter { it.versionCode > currentVersion }
                .maxByOrNull { it.versionCode }
        }
    }

    fun currentVersionLabel(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${packageInfo.versionName} (${getVersionCode(packageInfo)})"
    }

    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    suspend fun downloadAndInstall(context: Context, version: AppVersionDto): Result<Unit> {
        return try {
            if (!TokenManager.getInstance().isLoggedIn()) {
                return Result.failure(IllegalStateException("请先登录后再升级"))
            }
            if (!canInstallUnknownApps(context)) {
                openInstallPermissionSettings(context)
                return Result.failure(IllegalStateException("请先允许本应用安装未知来源应用"))
            }
            val file = download(context.applicationContext as Application, version)
            val validation = validateInstallCandidate(context, file)
            if (validation.isFailure) {
                return Result.failure(validation.exceptionOrNull() ?: IllegalStateException("主 APK 不可安装"))
            }
            val candidateVersionCode = getVersionCode(validation.getOrThrow())
            if (candidateVersionCode != version.versionCode) {
                return Result.failure(SecurityException(PackageIntegrityVerifier.VERIFY_FAILED_MESSAGE))
            }
            if (!PackageIntegrityVerifier.verifyBeforeUpgrade(
                    PackageIntegrityVerifier.PackageType.APP,
                    file,
                    candidateVersionCode,
                    version.checksum
                )
            ) {
                return Result.failure(SecurityException(PackageIntegrityVerifier.VERIFY_FAILED_MESSAGE))
            }
            installApk(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndInstall failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun download(application: Application, version: AppVersionDto): File {
        val request = Request.Builder().url(RetrofitClient.resolveUrl(version.apkUrl)).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("下载失败: ${response.code}")
        }
        val body = response.body ?: throw IOException("下载内容为空")
        val dir = File(application.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val file = File(dir, "app-${version.versionCode}.apk")
        body.byteStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private fun validateInstallCandidate(context: Context, apkFile: File): Result<PackageInfo> {
        return try {
            val packageManager = context.packageManager
            val candidate = packageManager.getPackageArchiveInfo(apkFile.absolutePath, signatureFlags())
                ?: return Result.failure(IOException("下载的文件不是有效 APK"))
            if (candidate.packageName != context.packageName) {
                return Result.failure(SecurityException("主 APK 包名不匹配: ${candidate.packageName}"))
            }
            val current = packageManager.getPackageInfo(context.packageName, signatureFlags())
            if (getVersionCode(candidate) <= getVersionCode(current)) {
                return Result.failure(IllegalStateException("当前已是最新版本或目标版本更低"))
            }
            if (signatureFingerprints(current) != signatureFingerprints(candidate)) {
                return Result.failure(SecurityException("主 APK 签名不一致，无法覆盖安装"))
            }
            Result.success(candidate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isMiui()) {
                installWithPackageInstaller(context, apkFile)
            } else {
                installWithIntent(context, apkFile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PackageInstaller failed, fallback to intent: ${e.message}", e)
            installWithIntent(context, apkFile)
        }
    }

    private fun installWithPackageInstaller(context: Context, apkFile: File): Result<Unit> {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setAppPackageName(context.packageName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            params.setSize(apkFile.length())
        }
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        session.openWrite("package", 0, apkFile.length()).use { output ->
            apkFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        val intent = Intent(context, AppUpdateReceiver::class.java)
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, piFlags)
        session.commit(pendingIntent.intentSender)
        Log.i(TAG, "App update install session committed: $sessionId")
        return Result.success(Unit)
    }

    private fun installWithIntent(context: Context, apkFile: File): Result<Unit> {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
        return Result.success(Unit)
    }

    private fun getCurrentVersionCode(context: Context): Int {
        return getVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }

    private fun signatureFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    private fun signatureFingerprints(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures ?: emptyArray()
        }
        return signatures.map { signatureFingerprint(it) }.toSet()
    }

    private fun signatureFingerprint(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun getVersionCode(packageInfo: PackageInfo): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun isMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        return manufacturer.equals("Xiaomi", ignoreCase = true) ||
                manufacturer.equals("Redmi", ignoreCase = true) ||
                brand.equals("Xiaomi", ignoreCase = true) ||
                brand.equals("Redmi", ignoreCase = true)
    }
}
