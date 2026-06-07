package com.duodian.admin.service;

import com.duodian.admin.config.CloneAuthorizationProperties;
import com.duodian.admin.entity.Shop;
import com.duodian.admin.entity.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CloneAuthorizationTokenService {
    public static final String DEFAULT_PUBLIC_KEY_ID = "rsa_2026_01";
    public static final String DEFAULT_PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqriGm/MjBo+b8Jh8wq3lGtFuampim/we1dOP00FRAW8Sc1n2tusSqCihem+Y5bkPRIg5Dt4yN+UIAsef6gPywqmKMSrdhEkb6MomeeSv55bB6bKFop+7jUTd5JrGXDmYQLJUZb1yEnn6D+kQ2KO3HdshCMUUT0MN2VSe79vmpp2Mhg6d6vAQ4NwSRlljrFDVpBLL8HuxphV3iJuDdC9WdwOyvhO2AhtROMnyZNgWzPF9UB8muHxpCgRtblONIVAu1v62HnJZkdIZzzZy3IT0V4ySXPm+7C6QVkwHXdb/RILd+hPDHFpk481uhhEuuTCBCwagy34vTHIgruik4msCdQIDAQAB";

    private final CloneAuthorizationProperties properties;
    private final PrivateKey privateKey;

    public CloneAuthorizationTokenService(CloneAuthorizationProperties properties) {
        this.properties = properties;
        String configuredPrivateKey = firstNonBlank(properties.getPrivateKey());
        if (configuredPrivateKey.isBlank()) {
            throw new IllegalStateException("app.clone-auth.private-key is required");
        }
        this.privateKey = loadPrivateKey(configuredPrivateKey);
    }

    public String signToken(Shop shop, User user) {
        if (shop.getCloneInstanceId() == null || shop.getCloneInstanceId().isBlank()) {
            throw new IllegalArgumentException("cloneInstanceId is required");
        }
        if (shop.getPackageName() == null || shop.getPackageName().isBlank()) {
            throw new IllegalArgumentException("packageName is required");
        }
        if (shop.getLocalVirtualUserId() == null || shop.getLocalVirtualUserId() < 0) {
            throw new IllegalArgumentException("localVirtualUserId is required");
        }
        LocalDateTime authStartAt = shop.getAuthStartAt() != null ? shop.getAuthStartAt() : LocalDateTime.now();
        LocalDateTime authExpireAt = shop.getAuthExpireAt() != null ? shop.getAuthExpireAt() : authStartAt.plusDays(30);
        long iat = toEpochSeconds(authStartAt);
        long exp = toEpochSeconds(authExpireAt);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", firstNonBlank(properties.getPublicKeyId(), DEFAULT_PUBLIC_KEY_ID));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "clone_auth");
        claims.put("serverUserId", shop.getUserId());
        claims.put("phone", user != null ? user.getPhone() : "");
        claims.put("cloneInstanceId", shop.getCloneInstanceId());
        claims.put("packageName", shop.getPackageName());
        claims.put("localVirtualUserId", shop.getLocalVirtualUserId());
        claims.put("credentialVersion", shop.getCredentialVersion() == null ? 1 : shop.getCredentialVersion());
        claims.put("authStartAt", iat);
        claims.put("authExpireAt", exp);
        claims.put("iat", iat);
        claims.put("exp", exp);
        claims.put("jti", shop.getAuthorizationJti());

        String signingInput = base64Url(toJson(header).getBytes(StandardCharsets.UTF_8))
                + "."
                + base64Url(toJson(claims).getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + sign(signingInput);
    }

    public String getPublicKeyId() {
        return firstNonBlank(properties.getPublicKeyId(), DEFAULT_PUBLIC_KEY_ID);
    }

    public String getPublicKeyBase64() {
        return DEFAULT_PUBLIC_KEY_BASE64;
    }

    private PrivateKey loadPrivateKey(String encoded) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid clone authorization private key", e);
        }
    }

    private String sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return base64Url(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign clone authorization token", e);
        }
    }

    private long toEpochSeconds(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toJson(Map<String, Object> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
