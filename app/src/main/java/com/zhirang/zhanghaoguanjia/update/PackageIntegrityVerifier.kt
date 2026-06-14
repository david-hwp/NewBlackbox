package com.zhirang.zhanghaoguanjia.update

import android.util.Log
import com.zhirang.zhanghaoguanjia.bean.dto.PackageVerifyRequest
import com.zhirang.zhanghaoguanjia.network.ApiResponse
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object PackageIntegrityVerifier {
    private const val TAG = "PackageIntegrityVerifier"
    const val VERIFY_FAILED_MESSAGE = "您使用的安装包未通过检验，不可升级"

    enum class PackageType {
        APP,
        ENGINE
    }

    suspend fun verifyBeforeUpgrade(
        type: PackageType,
        file: File,
        versionCode: Int,
        expectedChecksum: String?,
        packageName: String? = null
    ): Boolean {
        val md5 = computeFileDigest(file, "MD5") ?: return false
        val sha256 = computeFileDigest(file, "SHA-256") ?: return false
        val request = PackageVerifyRequest(
            versionCode = versionCode,
            md5 = md5,
            sha256 = sha256,
            packageName = packageName
        )
        val response = try {
            when (type) {
                PackageType.APP -> RetrofitClient.apiService.verifyAppPackage(request)
                PackageType.ENGINE -> RetrofitClient.apiService.verifyEnginePackage(request)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server package verification request failed: type=$type version=$versionCode message=${e.message}", e)
            return false
        }
        val valid = response.isSuccess() && response.data?.valid == true
        if (!valid) {
            Log.e(TAG, "Server package verification failed: type=$type version=$versionCode md5=$md5 sha256=$sha256")
        }
        return valid && verifyExpectedChecksum(type, file, expectedChecksum)
    }

    fun computeFileMd5(file: File): String? = computeFileDigest(file, "MD5")

    fun computeFileSha256(file: File): String? = computeFileDigest(file, "SHA-256")

    private fun verifyExpectedChecksum(type: PackageType, file: File, expectedChecksum: String?): Boolean {
        val expected = expectedChecksum?.trim()?.lowercase(Locale.ROOT)
        if (expected.isNullOrBlank()) {
            Log.e(TAG, "Missing expected checksum: type=$type file=${file.absolutePath}")
            return false
        }
        val algorithm = when (expected.length) {
            32 -> "MD5"
            64 -> "SHA-256"
            else -> {
                Log.e(TAG, "Unsupported checksum length: type=$type length=${expected.length}")
                return false
            }
        }
        val actual = computeFileDigest(file, algorithm) ?: return false
        val matched = actual.equals(expected, ignoreCase = true)
        if (!matched) {
            Log.e(TAG, "Checksum mismatch: type=$type algorithm=$algorithm expected=$expected actual=$actual")
        }
        return matched
    }

    private fun <T> ApiResponse<T>.isSuccess(): Boolean = code == 200

    private fun computeFileDigest(file: File, algorithm: String): String? {
        return try {
            val md = MessageDigest.getInstance(algorithm)
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Digest failed: algorithm=$algorithm file=${file.absolutePath} message=${e.message}", e)
            null
        }
    }
}
