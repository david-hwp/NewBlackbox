package com.duodian.admin.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@ConditionalOnProperty(name = "app.release.runner", havingValue = "script")
public class ScriptReleaseJobRunner implements ReleaseJobRunner {
    private static final Logger log = LoggerFactory.getLogger(ScriptReleaseJobRunner.class);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${app.release.script-path:admin/scripts/release-channel-apk.sh}")
    private String scriptPath;

    @Value("${app.release.repo-url:}")
    private String repoUrl;

    @Value("${app.release.workdir:}")
    private String workdir;

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

    @Override
    public void start(ReleaseJobRunContext context) {
        Path script = Path.of(scriptPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            throw new RuntimeException("发布脚本不存在: " + script);
        }
        if (!Files.isExecutable(script)) {
            throw new RuntimeException("发布脚本不可执行: " + script);
        }
        String effectiveRepoUrl = text(repoUrl);
        if (effectiveRepoUrl == null) {
            throw new RuntimeException("发布脚本仓库地址未配置");
        }

        ProcessBuilder builder = new ProcessBuilder(script.toString());
        String effectiveWorkdir = text(workdir);
        if (effectiveWorkdir != null) {
            builder.directory(new File(effectiveWorkdir));
        }
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile(context.jobId())));
        builder.environment().putAll(environment(context, effectiveRepoUrl));

        try {
            Process process = builder.start();
            executor.submit(() -> waitForProcess(context, process));
            log.info("Started release script for job={} channel={} dryRun={}", context.jobId(), context.channelCode(), dryRun);
        } catch (Exception e) {
            throw new RuntimeException("发布脚本启动失败: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private Map<String, String> environment(ReleaseJobRunContext context, String effectiveRepoUrl) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("JOB_ID", String.valueOf(context.jobId()));
        env.put("CHANNEL_CODE", context.channelCode());
        env.put("APP_APPLICATION_ID", context.appApplicationId());
        env.put("ENGINE_APPLICATION_ID", context.engineApplicationId());
        env.put("ENGINE_DATA_ROOT_NAME", engineDataRootName(context.channelCode()));
        env.put("API_BASE_URL", apiBaseUrl(context.channelCode()));
        env.put("APP_NAME", context.appDisplayName());
        env.put("ENGINE_NAME", context.engineDisplayName());
        env.put("SOURCE_RELEASE_BRANCH", context.sourceReleaseBranch());
        env.put("CHANNEL_RELEASE_BRANCH", context.channelReleaseBranch());
        env.put("APP_VERSION_NAME", context.appVersionName());
        env.put("APP_VERSION_CODE", String.valueOf(context.appVersionCode()));
        env.put("ENGINE_VERSION_NAME", context.engineVersionName());
        env.put("ENGINE_VERSION_CODE", String.valueOf(context.engineVersionCode()));
        env.put("REPO_URL", effectiveRepoUrl);
        env.put("BACKEND_BASE_URL", context.backendBaseUrl());
        env.put("PROGRESS_CALLBACK_URL", context.progressCallbackUrl());
        env.put("COMPLETE_CALLBACK_URL", context.completeCallbackUrl());
        env.put("JOB_TOKEN", context.callbackToken());
        env.put("ADMIN_AUTHORIZATION_TOKEN", context.adminAuthorizationToken());
        env.put("DRY_RUN", Boolean.toString(dryRun));
        env.put("PUSH_RELEASE_BRANCH", Boolean.toString(pushReleaseBranch));
        putIfPresent(env, "LOCK_DIR", lockDir);
        putIfPresent(env, "WORK_ROOT", workRoot);
        return env;
    }

    private void waitForProcess(ReleaseJobRunContext context, Process process) {
        try {
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Release script completed for job={} channel={}", context.jobId(), context.channelCode());
            } else {
                log.warn("Release script exited with code {} for job={} channel={}", exitCode, context.jobId(), context.channelCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Release script wait interrupted for job={} channel={}", context.jobId(), context.channelCode());
        }
    }

    private File logFile(Long jobId) {
        String root = text(workRoot);
        Path dir = root == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "duodian-release-job-logs")
                : Path.of(root).resolve("logs");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("发布脚本日志目录创建失败: " + e.getMessage(), e);
        }
        return dir.resolve("release-job-" + jobId + ".log").toFile();
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

    private String apiBaseUrl(String channelCode) {
        String configured = text(apiBaseUrl);
        if (configured == null) {
            return "http://dpgj.zrnh.cn/api/";
        }
        return configured;
    }

    private String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
