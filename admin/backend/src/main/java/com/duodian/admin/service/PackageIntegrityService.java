package com.duodian.admin.service;

import com.duodian.admin.controller.dto.PackageVerifyRequest;
import com.duodian.admin.service.FileStorageService.DownloadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PackageIntegrityService {
    private static final Logger log = LoggerFactory.getLogger(PackageIntegrityService.class);

    private final FileStorageService fileStorageService;

    public PackageIntegrityService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public boolean verify(
            String packageType,
            String apkUrl,
            Integer releaseVersionCode,
            String releaseChecksum,
            String expectedPackageName,
            PackageVerifyRequest request
    ) {
        if (request == null || request.getVersionCode() == null || releaseVersionCode == null) {
            return false;
        }
        if (!releaseVersionCode.equals(request.getVersionCode())) {
            log.warn("{} package versionCode mismatch: release={} request={}",
                    packageType, releaseVersionCode, request.getVersionCode());
            return false;
        }
        String requestedMd5 = normalizeHex(request.getMd5(), 32);
        String requestedSha256 = normalizeHex(request.getSha256(), 64);
        if (requestedMd5 == null || requestedSha256 == null) {
            log.warn("{} package digest request invalid: version={}", packageType, request.getVersionCode());
            return false;
        }
        String expectedPackage = normalizePackageName(expectedPackageName);
        String requestedPackage = normalizePackageName(request.getPackageName());
        if (expectedPackage != null && requestedPackage != null && !expectedPackage.equals(requestedPackage)) {
            log.warn("{} package name mismatch: version={} expected={} request={}",
                    packageType, request.getVersionCode(), expectedPackage, requestedPackage);
            return false;
        }
        String normalizedReleaseChecksum = normalizeChecksum(releaseChecksum);
        if (normalizedReleaseChecksum == null) {
            log.warn("{} package release checksum missing or invalid: version={}", packageType, request.getVersionCode());
            return false;
        }
        try {
            DownloadedFile file = fileStorageService.downloadByDownloadUrl(apkUrl);
            if (file == null || file.getBytes() == null || file.getBytes().length == 0) {
                log.warn("{} package file not found: version={} url={}", packageType, request.getVersionCode(), apkUrl);
                return false;
            }
            String actualMd5 = digest(file.getBytes(), "MD5");
            String actualSha256 = digest(file.getBytes(), "SHA-256");
            boolean releaseChecksumValid = normalizedReleaseChecksum.equals(actualMd5) ||
                    normalizedReleaseChecksum.equals(actualSha256);
            boolean valid = releaseChecksumValid && requestedMd5.equals(actualMd5) && requestedSha256.equals(actualSha256);
            if (!valid) {
                log.warn("{} package digest mismatch: version={} releaseChecksum={} requestMd5={} actualMd5={} requestSha256={} actualSha256={}",
                        packageType, request.getVersionCode(), normalizedReleaseChecksum,
                        requestedMd5, actualMd5, requestedSha256, actualSha256);
            }
            return valid;
        } catch (Exception e) {
            log.warn("{} package verification failed: version={} url={} message={}",
                    packageType, request.getVersionCode(), apkUrl, e.getMessage());
            return false;
        }
    }

    private String normalizeChecksum(String value) {
        String md5 = normalizeHex(value, 32);
        if (md5 != null) {
            return md5;
        }
        return normalizeHex(value, 64);
    }

    private String normalizePackageName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeHex(String value, int expectedLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != expectedLength || !normalized.matches("[0-9a-f]+")) {
            return null;
        }
        return normalized;
    }

    private String digest(byte[] bytes, String algorithm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }
}
