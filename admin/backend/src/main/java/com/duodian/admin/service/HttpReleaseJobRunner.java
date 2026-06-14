package com.duodian.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.release.runner", havingValue = "http")
public class HttpReleaseJobRunner implements ReleaseJobRunner {
    private static final Logger log = LoggerFactory.getLogger(HttpReleaseJobRunner.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.release.worker-url:}")
    private String workerUrl;

    @Value("${app.release.worker-token:}")
    private String workerToken;

    @Value("${app.release.repo-url:}")
    private String repoUrl;

    @Value("${app.release.dry-run:false}")
    private boolean dryRun;

    @Value("${app.release.push-release-branch:true}")
    private boolean pushReleaseBranch;

    @Value("${app.release.lock-dir:}")
    private String lockDir;

    @Value("${app.release.work-root:}")
    private String workRoot;

    @Value("${app.release.api-base-url:http://dpgj.zrnh.cn/api/}")
    private String apiBaseUrl;

    @Value("${app.release.worker-backend-base-url:}")
    private String workerBackendBaseUrl;

    @Override
    public void start(ReleaseJobRunContext context) {
        String normalizedWorkerUrl = requireText(workerUrl, "发布 worker 地址未配置");
        String normalizedWorkerToken = requireText(workerToken, "发布 worker token 未配置");
        String normalizedRepoUrl = requireText(repoUrl, "发布脚本仓库地址未配置");
        String payload = toJson(environment(context, normalizedRepoUrl));

        HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedWorkerUrl))
                .timeout(Duration.ofSeconds(15))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + normalizedWorkerToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("发布 worker 返回异常: HTTP " + response.statusCode());
            }
            log.info("Submitted release job to HTTP worker: job={} channel={} dryRun={}",
                    context.jobId(), context.channelCode(), dryRun);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("发布 worker 请求被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("发布 worker 请求失败: " + e.getMessage(), e);
        }
    }

    private Map<String, String> environment(ReleaseJobRunContext context, String normalizedRepoUrl) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("JOB_ID", String.valueOf(context.jobId()));
        env.put("CHANNEL_CODE", context.channelCode());
        env.put("APP_APPLICATION_ID", context.appApplicationId());
        env.put("ENGINE_APPLICATION_ID", context.engineApplicationId());
        env.put("ENGINE_DATA_ROOT_NAME", engineDataRootName(context.channelCode()));
        env.put("API_BASE_URL", apiBaseUrl());
        env.put("APP_NAME", context.appDisplayName());
        env.put("ENGINE_NAME", context.engineDisplayName());
        env.put("SOURCE_RELEASE_BRANCH", context.sourceReleaseBranch());
        env.put("CHANNEL_RELEASE_BRANCH", context.channelReleaseBranch());
        env.put("APP_VERSION_NAME", context.appVersionName());
        env.put("APP_VERSION_CODE", String.valueOf(context.appVersionCode()));
        env.put("ENGINE_VERSION_NAME", context.engineVersionName());
        env.put("ENGINE_VERSION_CODE", String.valueOf(context.engineVersionCode()));
        env.put("REPO_URL", normalizedRepoUrl);
        env.put("BACKEND_BASE_URL", backendBaseUrlForWorker(context));
        env.put("PROGRESS_CALLBACK_URL", callbackUrlForWorker(context.progressCallbackUrl()));
        env.put("COMPLETE_CALLBACK_URL", callbackUrlForWorker(context.completeCallbackUrl()));
        env.put("JOB_TOKEN", context.callbackToken());
        env.put("ADMIN_AUTHORIZATION_TOKEN", context.adminAuthorizationToken());
        env.put("DRY_RUN", Boolean.toString(dryRun));
        env.put("PUSH_RELEASE_BRANCH", Boolean.toString(pushReleaseBranch));
        putIfPresent(env, "LOCK_DIR", lockDir);
        putIfPresent(env, "WORK_ROOT", workRoot);
        return env;
    }

    private void putIfPresent(Map<String, String> env, String key, String value) {
        String normalized = text(value);
        if (normalized != null) {
            env.put(key, normalized);
        }
    }

    private String engineDataRootName(String channelCode) {
        String normalized = text(channelCode);
        if (normalized == null || "main".equalsIgnoreCase(normalized)) {
            return "blackbox";
        }
        return "blackbox-" + normalized.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private String apiBaseUrl() {
        String configured = text(apiBaseUrl);
        if (configured == null) {
            return "http://dpgj.zrnh.cn/api/";
        }
        return configured;
    }

    private String backendBaseUrlForWorker(ReleaseJobRunContext context) {
        String configured = text(workerBackendBaseUrl);
        if (configured != null) {
            return configured;
        }
        return context.backendBaseUrl();
    }

    private String callbackUrlForWorker(String callbackUrl) {
        String configured = text(workerBackendBaseUrl);
        if (configured == null) {
            return callbackUrl;
        }
        String normalizedCallback = requireText(callbackUrl, "发布任务回调地址不能为空");
        int apiIndex = normalizedCallback.indexOf("/api/");
        if (apiIndex >= 0) {
            return normalizeBaseUrl(configured) + normalizedCallback.substring(apiIndex + "/api".length());
        }
        if (normalizedCallback.startsWith("/api/")) {
            return normalizeBaseUrl(configured) + normalizedCallback.substring("/api".length());
        }
        if (normalizedCallback.startsWith("/")) {
            return normalizeBaseUrl(configured) + normalizedCallback;
        }
        return normalizeBaseUrl(configured) + "/" + normalizedCallback;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = requireText(value, "发布 worker 回调后端地址未配置");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = text(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toJson(Map<String, String> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(jsonEscape(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(jsonEscape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
