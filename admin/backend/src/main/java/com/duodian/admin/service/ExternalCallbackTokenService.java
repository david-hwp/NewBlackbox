package com.duodian.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class ExternalCallbackTokenService {
    public static final String HEADER_NAME = "X-External-Callback-Token";

    private final String token;

    public ExternalCallbackTokenService(@Value("${app.external-callback.token:}") String token) {
        this.token = normalizeToken(token);
    }

    public boolean hasProvidedToken(String provided) {
        return normalizeToken(provided) != null;
    }

    public boolean isValidToken(String provided) {
        String expected = normalizeToken(token);
        String actual = normalizeToken(provided);
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(expected), sha256(actual));
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
