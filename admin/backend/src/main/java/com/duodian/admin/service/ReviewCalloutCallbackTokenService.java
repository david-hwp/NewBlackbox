package com.duodian.admin.service;

import com.duodian.admin.config.ReviewCalloutProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class ReviewCalloutCallbackTokenService {
    public static final String HEADER_NAME = "X-External-Callback-Token";

    private final ReviewCalloutProperties properties;

    public ReviewCalloutCallbackTokenService(ReviewCalloutProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getCallback().isEnabled();
    }

    public boolean isValid(String headerToken, String queryToken) {
        String expected = normalize(properties.getCallback().getToken());
        String actual = normalize(headerToken);
        if (actual == null && properties.getCallback().isAcceptQueryToken()) {
            actual = normalize(queryToken);
        }
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(expected), sha256(actual));
    }

    private String normalize(String value) {
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
