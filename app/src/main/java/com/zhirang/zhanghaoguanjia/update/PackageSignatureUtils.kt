package com.zhirang.zhanghaoguanjia.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

object PackageSignatureUtils {
    @Suppress("DEPRECATION")
    fun signatureFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }
    }

    fun signaturesCompatible(installed: PackageInfo, candidate: PackageInfo): Boolean {
        val installedFingerprints = signatureFingerprints(installed)
        val candidateFingerprints = signatureFingerprints(candidate)
        return installedFingerprints.isNotEmpty() && installedFingerprints == candidateFingerprints
    }

    fun signatureFingerprints(packageInfo: PackageInfo): Set<String> {
        val signatures = mutableListOf<Signature>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                val currentSigners = signingInfo.apkContentsSigners
                if (!currentSigners.isNullOrEmpty()) {
                    signatures.addAll(currentSigners)
                } else {
                    val history = signingInfo.signingCertificateHistory
                    if (!history.isNullOrEmpty()) {
                        signatures.addAll(history)
                    }
                }
            }
        }
        if (signatures.isEmpty()) {
            @Suppress("DEPRECATION")
            val legacySignatures = packageInfo.signatures
            if (!legacySignatures.isNullOrEmpty()) {
                signatures.addAll(legacySignatures)
            }
        }
        return signatures.map { signatureFingerprint(it) }.toSet()
    }

    private fun signatureFingerprint(signature: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
