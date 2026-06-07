package top.niunaijun.blackbox.engine

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class CloneAuthTokenVerifierTest {

    @Test
    fun validTokenPassesVerification() {
        val keyPair = generateKeyPair()
        val token = signToken(keyPair)
        val result = verify(token, keyPair)

        assertTrue(result.reason, result.valid)
    }

    @Test
    fun tamperedTokenFailsVerification() {
        val keyPair = generateKeyPair()
        val token = signToken(keyPair)
        val tamperedClaims = base64Url(
            claimsJson(localVirtualUserId = 9).toByteArray(Charsets.UTF_8)
        )
        val parts = token.split(".")
        val tamperedToken = "${parts[0]}.$tamperedClaims.${parts[2]}"
        val result = verify(tamperedToken, keyPair)

        assertFalse(result.valid)
    }

    @Test
    fun mismatchedLocalVirtualUserFailsVerification() {
        val keyPair = generateKeyPair()
        val token = signToken(keyPair)
        val result = CloneAuthTokenVerifier.verifyCloneAuth(
            meta(),
            token,
            CLONE_ID,
            PACKAGE_NAME,
            SERVER_USER_ID,
            9,
            NOW,
            publicKeyResolver(keyPair),
            ::decodeBase64
        )

        assertFalse(result.valid)
    }

    @Test
    fun tokenWithinClockSkewPassesVerification() {
        val keyPair = generateKeyPair()
        val token = signToken(keyPair, authStartAt = NOW + 120)
        val result = verify(token, keyPair)

        assertTrue(result.reason, result.valid)
    }

    private fun verify(
        token: String,
        keyPair: KeyPair
    ): CloneAuthTokenVerifier.VerificationResult {
        return CloneAuthTokenVerifier.verifyCloneAuth(
            meta(),
            token,
            CLONE_ID,
            PACKAGE_NAME,
            SERVER_USER_ID,
            LOCAL_USER_ID,
            NOW,
            publicKeyResolver(keyPair),
            ::decodeBase64
        )
    }

    private fun signToken(keyPair: KeyPair, authStartAt: Long = NOW - 60): String {
        val header = JSONObject()
            .put("alg", "RS256")
            .put("typ", "JWT")
            .put("kid", KEY_ID)
            .toString()
        val signingInput = base64Url(header.toByteArray(Charsets.UTF_8)) +
                "." +
                base64Url(claimsJson(authStartAt = authStartAt).toByteArray(Charsets.UTF_8))
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(signingInput.toByteArray(Charsets.UTF_8))
        return "$signingInput.${base64Url(signature.sign())}"
    }

    private fun claimsJson(
        localVirtualUserId: Int = LOCAL_USER_ID,
        authStartAt: Long = NOW - 60
    ): String {
        return JSONObject()
            .put("typ", "clone_auth")
            .put("serverUserId", SERVER_USER_ID)
            .put("phone", PHONE)
            .put("cloneInstanceId", CLONE_ID)
            .put("packageName", PACKAGE_NAME)
            .put("localVirtualUserId", localVirtualUserId)
            .put("credentialVersion", 1)
            .put("authStartAt", authStartAt)
            .put("authExpireAt", NOW + 3600)
            .put("iat", authStartAt)
            .put("exp", NOW + 3600)
            .put("jti", "jti-test")
            .toString()
    }

    private fun meta(): JSONObject {
        return JSONObject()
            .put("version", 1)
            .put("cloneInstanceId", CLONE_ID)
            .put("packageName", PACKAGE_NAME)
            .put("serverUserId", SERVER_USER_ID)
            .put("phone", PHONE)
            .put("localVirtualUserId", LOCAL_USER_ID)
            .put("publicKeyId", KEY_ID)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    private fun publicKeyResolver(keyPair: KeyPair): (String) -> String? {
        return { keyId ->
            if (keyId == KEY_ID) {
                Base64.getEncoder().encodeToString(keyPair.public.encoded)
            } else {
                null
            }
        }
    }

    private fun decodeBase64(value: String, urlSafe: Boolean): ByteArray {
        return if (urlSafe) {
            Base64.getUrlDecoder().decode(value)
        } else {
            Base64.getDecoder().decode(value)
        }
    }

    private fun base64Url(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val KEY_ID = "test-key"
        private const val SERVER_USER_ID = 7L
        private const val LOCAL_USER_ID = 3
        private const val PHONE = "13800000007"
        private const val PACKAGE_NAME = "com.jd.mrd.jingming"
        private const val CLONE_ID = "CLN1-13800000007-com.jd.mrd.jingming-N1-U3-Rabcd1234"
        private const val NOW = 1_720_000_000L
    }
}
