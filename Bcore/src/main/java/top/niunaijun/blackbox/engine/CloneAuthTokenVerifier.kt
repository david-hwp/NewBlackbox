package top.niunaijun.blackbox.engine

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

object CloneAuthTokenVerifier {
    const val DEFAULT_PUBLIC_KEY_ID = "rsa_2026_01"
    private const val DEFAULT_PUBLIC_KEY_BASE64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqriGm/MjBo+b8Jh8wq3lGtFuampim/we1dOP00FRAW8Sc1n2tusSqCihem+Y5bkPRIg5Dt4yN+UIAsef6gPywqmKMSrdhEkb6MomeeSv55bB6bKFop+7jUTd5JrGXDmYQLJUZb1yEnn6D+kQ2KO3HdshCMUUT0MN2VSe79vmpp2Mhg6d6vAQ4NwSRlljrFDVpBLL8HuxphV3iJuDdC9WdwOyvhO2AhtROMnyZNgWzPF9UB8muHxpCgRtblONIVAu1v62HnJZkdIZzzZy3IT0V4ySXPm+7C6QVkwHXdb/RILd+hPDHFpk481uhhEuuTCBCwagy34vTHIgruik4msCdQIDAQAB"
    private val publicKeys = mapOf(DEFAULT_PUBLIC_KEY_ID to DEFAULT_PUBLIC_KEY_BASE64)

    fun verifyCloneAuth(
        meta: JSONObject?,
        token: String?,
        cloneInstanceId: String,
        packageName: String,
        serverUserId: Long,
        localVirtualUserId: Int,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
        publicKeyResolver: (String) -> String? = { publicKeys[it] },
        base64Decoder: (String, Boolean) -> ByteArray = ::decodeBase64
    ): VerificationResult {
        if (meta == null) {
            return VerificationResult(false, "授权元数据不存在")
        }
        val normalizedToken = token?.trim()?.takeIf { it.isNotEmpty() }
            ?: return VerificationResult(false, "授权令牌不存在")
        if (meta.optString("cloneInstanceId") != cloneInstanceId ||
            meta.optString("packageName") != packageName ||
            meta.optLong("serverUserId", -1L) != serverUserId ||
            meta.optInt("localVirtualUserId", -1) != localVirtualUserId
        ) {
            return VerificationResult(false, "授权元数据不匹配")
        }

        val parts = normalizedToken.split(".")
        if (parts.size != 3) {
            return VerificationResult(false, "授权令牌格式错误")
        }
        val header = decodeJson(parts[0], base64Decoder) ?: return VerificationResult(false, "授权头解析失败")
        val claims = decodeJson(parts[1], base64Decoder) ?: return VerificationResult(false, "授权内容解析失败")
        val keyId = header.optString("kid").takeIf { it.isNotBlank() }
            ?: meta.optString("publicKeyId").takeIf { it.isNotBlank() }
            ?: DEFAULT_PUBLIC_KEY_ID
        val publicKey = publicKeyResolver(keyId) ?: return VerificationResult(false, "授权公钥不存在")
        if (!verifySignature("${parts[0]}.${parts[1]}", parts[2], publicKey, base64Decoder)) {
            return VerificationResult(false, "授权签名校验失败")
        }

        if (claims.optString("typ") != "clone_auth") {
            return VerificationResult(false, "授权类型错误")
        }
        if (claims.optLong("serverUserId", -1L) != serverUserId ||
            claims.optString("cloneInstanceId") != cloneInstanceId ||
            claims.optString("packageName") != packageName ||
            claims.optInt("localVirtualUserId", -1) != localVirtualUserId
        ) {
            return VerificationResult(false, "授权内容不匹配")
        }
        val start = claims.optLong("authStartAt", -1L)
        val expire = claims.optLong("authExpireAt", -1L)
        val exp = claims.optLong("exp", expire)
        if (start < 0 || expire <= 0 || nowSeconds < start || nowSeconds >= expire || nowSeconds >= exp) {
            return VerificationResult(false, "授权已过期")
        }
        return VerificationResult(true, "ok")
    }

    private fun decodeJson(part: String, base64Decoder: (String, Boolean) -> ByteArray): JSONObject? {
        return runCatching {
            JSONObject(String(base64Decoder(part, true)))
        }.getOrNull()
    }

    private fun verifySignature(
        signingInput: String,
        signaturePart: String,
        publicKeyBase64: String,
        base64Decoder: (String, Boolean) -> ByteArray
    ): Boolean {
        return runCatching {
            val keyBytes = base64Decoder(publicKeyBase64, false)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes)) as RSAPublicKey
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(publicKey)
            signature.update(signingInput.toByteArray(Charsets.UTF_8))
            signature.verify(base64Decoder(signaturePart, true))
        }.getOrDefault(false)
    }

    private fun decodeBase64(value: String, urlSafe: Boolean): ByteArray {
        val flags = if (urlSafe) {
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        } else {
            Base64.NO_WRAP
        }
        return Base64.decode(value, flags)
    }

    data class VerificationResult(
        val valid: Boolean,
        val reason: String
    )
}
