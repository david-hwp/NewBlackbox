package com.duodian.admin.service;

import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.ReleaseJobCompleteRequest;
import com.duodian.admin.controller.dto.ReleaseJobCreateRequest;
import com.duodian.admin.controller.dto.ReleaseJobProgressRequest;
import com.duodian.admin.controller.dto.ReleaseJobResponse;
import com.duodian.admin.entity.Announcement;
import com.duodian.admin.entity.AppVersion;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.EngineVersion;
import com.duodian.admin.entity.ReleaseJob;
import com.duodian.admin.repository.AnnouncementRepository;
import com.duodian.admin.repository.AppVersionRepository;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.EngineVersionRepository;
import com.duodian.admin.repository.ReleaseJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class ReleaseJobService {
    public static final String CALLBACK_TOKEN_HEADER = "X-Release-Job-Token";
    public static final String APP_RELEASE_TYPE = "APP_RELEASE";
    public static final String APP_RELEASE_TITLE = "新版本发布";

    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;
    private static final int LOG_EXCERPT_LIMIT = 8000;
    private static final Set<String> ACTIVE_STATUSES = Set.of(ReleaseJob.STATUS_PENDING, ReleaseJob.STATUS_RUNNING);
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            ReleaseJob.STATUS_SUCCESS,
            ReleaseJob.STATUS_FAILED,
            ReleaseJob.STATUS_CANCELLED
    );

    private final ReleaseJobRepository releaseJobRepository;
    private final ChannelRepository channelRepository;
    private final AppVersionRepository appVersionRepository;
    private final EngineVersionRepository engineVersionRepository;
    private final AnnouncementRepository announcementRepository;
    private final PermissionService permissionService;
    private final ReleaseJobRunner releaseJobRunner;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.release.backend-base-url:}")
    private String backendBaseUrl;

    public ReleaseJobService(
            ReleaseJobRepository releaseJobRepository,
            ChannelRepository channelRepository,
            AppVersionRepository appVersionRepository,
            EngineVersionRepository engineVersionRepository,
            AnnouncementRepository announcementRepository,
            PermissionService permissionService,
            ReleaseJobRunner releaseJobRunner
    ) {
        this.releaseJobRepository = releaseJobRepository;
        this.channelRepository = channelRepository;
        this.appVersionRepository = appVersionRepository;
        this.engineVersionRepository = engineVersionRepository;
        this.announcementRepository = announcementRepository;
        this.permissionService = permissionService;
        this.releaseJobRunner = releaseJobRunner;
    }

    public PagedResponse<ReleaseJobResponse> list(Long channelId, String status, Integer page, Integer size) {
        permissionService.requireSuperAdmin();
        Long effectiveChannelId = normalizeChannelId(channelId);
        String normalizedStatus = normalizeStatusFilter(status);
        Page<ReleaseJobResponse> jobs = releaseJobRepository.searchReleaseJobs(
                ACTIVE,
                effectiveChannelId,
                normalizedStatus,
                PageRequest.of(pageNumber(page) - 1, pageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toResponse);
        return PagedResponse.from(jobs);
    }

    public ReleaseJobResponse get(Long id) {
        permissionService.requireSuperAdmin();
        return toResponse(findActiveJob(id));
    }

    @Transactional
    public ReleaseJobResponse create(ReleaseJobCreateRequest request) {
        permissionService.requireSuperAdmin();
        Channel channel = resolveMutableChannel(request == null ? null : request.getChannelId());
        requireNoActiveJob(channel.getId());

        ReleaseJob job = new ReleaseJob();
        fillFromRequest(job, request);
        job.setChannelId(channel.getId());
        job.setRequestedBy(permissionService.currentPrincipal().getUserId());
        job.setStatus(ReleaseJob.STATUS_PENDING);
        job.setProgress(0);
        job.setDeleted(ACTIVE);
        job.setLogExcerpt("Release job created for channel " + channel.getCode());
        return toResponse(releaseJobRepository.save(job), channel);
    }

    @Transactional
    public ReleaseJobResponse start(Long id) {
        permissionService.requireSuperAdmin();
        ReleaseJob job = findActiveJob(id);
        if (!ReleaseJob.STATUS_PENDING.equals(job.getStatus())) {
            throw new RuntimeException("只有待启动任务可以启动");
        }
        Channel channel = resolveMutableChannel(job.getChannelId());
        requireNoOtherActiveJob(channel.getId(), job.getId());
        launch(job, channel);
        return toResponse(job, channel);
    }

    @Transactional
    public ReleaseJobResponse retry(Long id) {
        permissionService.requireSuperAdmin();
        ReleaseJob original = findActiveJob(id);
        if (!ReleaseJob.STATUS_FAILED.equals(original.getStatus())) {
            throw new RuntimeException("只有失败任务可以重试");
        }
        Channel channel = resolveMutableChannel(original.getChannelId());
        requireNoActiveJob(channel.getId());

        ReleaseJob retry = new ReleaseJob();
        retry.setChannelId(original.getChannelId());
        retry.setSourceReleaseBranch(original.getSourceReleaseBranch());
        retry.setChannelReleaseBranch(original.getChannelReleaseBranch());
        retry.setAppVersionName(original.getAppVersionName());
        retry.setAppVersionCode(original.getAppVersionCode());
        retry.setEngineVersionName(original.getEngineVersionName());
        retry.setEngineVersionCode(original.getEngineVersionCode());
        retry.setAnnouncementContent(original.getAnnouncementContent());
        retry.setRetryOfJobId(original.getId());
        retry.setRequestedBy(permissionService.currentPrincipal().getUserId());
        retry.setStatus(ReleaseJob.STATUS_PENDING);
        retry.setProgress(0);
        retry.setDeleted(ACTIVE);
        retry.setLogExcerpt("Retry created from release job " + original.getId());
        retry = releaseJobRepository.save(retry);

        launch(retry, channel);
        return toResponse(retry, channel);
    }

    @Transactional
    public ReleaseJobResponse cancel(Long id) {
        permissionService.requireSuperAdmin();
        ReleaseJob job = findActiveJob(id);
        if (!ReleaseJob.STATUS_PENDING.equals(job.getStatus())) {
            throw new RuntimeException("只有待启动任务可以取消");
        }
        job.setStatus(ReleaseJob.STATUS_CANCELLED);
        job.setFinishedAt(LocalDateTime.now());
        appendLog(job, "Release job cancelled before worker start", null);
        return toResponse(releaseJobRepository.save(job));
    }

    @Transactional
    public ReleaseJobResponse progress(
            Long id,
            String token,
            String channelCode,
            ReleaseJobProgressRequest request
    ) {
        ReleaseJob job = verifyCallback(id, token, channelCode);
        if (!ReleaseJob.STATUS_RUNNING.equals(job.getStatus())) {
            throw new RuntimeException("任务未运行，不能更新进度");
        }
        if (request != null && request.getProgress() != null) {
            job.setProgress(Math.min(99, Math.max(0, request.getProgress())));
        }
        if (request != null) {
            appendLog(job, request.getLogExcerpt(), token);
        }
        return toResponse(releaseJobRepository.save(job));
    }

    @Transactional
    public ReleaseJobResponse complete(
            Long id,
            String token,
            String channelCode,
            ReleaseJobCompleteRequest request
    ) {
        ReleaseJob job = verifyCallback(id, token, channelCode);
        if (!ReleaseJob.STATUS_RUNNING.equals(job.getStatus())) {
            throw new RuntimeException("任务未运行，不能完成");
        }

        boolean success = isSuccess(request);
        if (!success) {
            job.setStatus(ReleaseJob.STATUS_FAILED);
            if (request != null && request.getProgress() != null) {
                job.setProgress(Math.min(99, Math.max(0, request.getProgress())));
            }
            appendFailureLog(job, request, token);
            job.setFinishedAt(LocalDateTime.now());
            return toResponse(releaseJobRepository.save(job));
        }

        recordArtifacts(job, request);
        requireSuccessfulArtifacts(job);
        job.setStatus(ReleaseJob.STATUS_SUCCESS);
        job.setProgress(100);
        appendLog(job, request == null ? null : request.getLogExcerpt(), token);
        job.setFinishedAt(LocalDateTime.now());
        publishRelease(job);
        return toResponse(releaseJobRepository.save(job));
    }

    @Transactional
    public void delete(Long id) {
        permissionService.requireSuperAdmin();
        ReleaseJob job = findActiveJob(id);
        if (!TERMINAL_STATUSES.contains(job.getStatus())) {
            throw new RuntimeException("运行中任务不能删除");
        }
        job.setDeleted(DELETED);
        releaseJobRepository.save(job);
    }

    private void launch(ReleaseJob job, Channel channel) {
        String token = generateToken();
        job.setCallbackTokenHash(hashToken(token));
        job.setStatus(ReleaseJob.STATUS_RUNNING);
        job.setProgress(Math.max(1, job.getProgress() == null ? 0 : job.getProgress()));
        job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(null);
        appendLog(job, "Release worker start requested for channel " + channel.getCode(), token);
        releaseJobRepository.save(job);

        String progressCallbackPath = "/api/release-jobs/" + job.getId() + "/callback/progress";
        String completeCallbackPath = "/api/release-jobs/" + job.getId() + "/callback/complete";
        try {
            releaseJobRunner.start(new ReleaseJobRunContext(
                    job.getId(),
                    channel.getCode(),
                    channel.getAppApplicationId(),
                    channel.getEngineApplicationId(),
                    firstText(channel.getAppDisplayName(), channel.getName()),
                    firstText(channel.getEngineDisplayName(), channel.getName() + "引擎"),
                    job.getSourceReleaseBranch(),
                    job.getChannelReleaseBranch(),
                    job.getAppVersionName(),
                    job.getAppVersionCode(),
                    job.getEngineVersionName(),
                    job.getEngineVersionCode(),
                    normalizeBackendBaseUrl(),
                    callbackUrl(progressCallbackPath),
                    callbackUrl(completeCallbackPath),
                    token
            ));
        } catch (RuntimeException e) {
            job.setStatus(ReleaseJob.STATUS_FAILED);
            job.setFinishedAt(LocalDateTime.now());
            appendLog(job, "Release worker failed to start: " + safeError(e), token);
            releaseJobRepository.save(job);
        }
    }

    private void publishRelease(ReleaseJob job) {
        AppVersion appVersion = appVersionRepository
                .findByChannelIdAndVersionCodeAndDeleted(job.getChannelId(), job.getAppVersionCode(), ACTIVE)
                .orElseGet(AppVersion::new);
        appVersion.setChannelId(job.getChannelId());
        appVersion.setVersionCode(job.getAppVersionCode());
        appVersion.setVersionName(job.getAppVersionName());
        appVersion.setApkUrl(job.getAppArtifactUrl());
        appVersion.setChecksum(primaryChecksum(job.getAppArtifactSha256(), job.getAppArtifactMd5()));
        appVersion.setFileSize(job.getAppArtifactSize());
        appVersion.setChangelog(job.getAnnouncementContent());
        appVersion.setPublished(true);
        appVersion.setDeleted(ACTIVE);
        appVersionRepository.save(appVersion);

        EngineVersion engineVersion = engineVersionRepository
                .findByChannelIdAndVersionCodeAndDeleted(job.getChannelId(), job.getEngineVersionCode(), ACTIVE)
                .orElseGet(EngineVersion::new);
        engineVersion.setChannelId(job.getChannelId());
        engineVersion.setVersionCode(job.getEngineVersionCode());
        engineVersion.setVersionName(job.getEngineVersionName());
        engineVersion.setApkUrl(job.getEngineArtifactUrl());
        engineVersion.setChecksum(primaryChecksum(job.getEngineArtifactSha256(), job.getEngineArtifactMd5()));
        engineVersion.setChangelog(job.getAnnouncementContent());
        engineVersion.setAvailable(true);
        engineVersion.setDeleted(ACTIVE);
        engineVersionRepository.save(engineVersion);

        Announcement announcement = announcementRepository
                .findFirstByChannelIdAndTypeAndDeletedOrderByCreatedAtDesc(job.getChannelId(), APP_RELEASE_TYPE, ACTIVE)
                .orElseGet(Announcement::new);
        announcement.setChannelId(job.getChannelId());
        announcement.setType(APP_RELEASE_TYPE);
        announcement.setTitle(APP_RELEASE_TITLE);
        announcement.setContent(job.getAnnouncementContent());
        announcement.setPublished(true);
        announcement.setDeleted(ACTIVE);
        announcementRepository.save(announcement);
    }

    private ReleaseJob verifyCallback(Long id, String token, String channelCode) {
        ReleaseJob job = findActiveJob(id);
        if (!matchesToken(job.getCallbackTokenHash(), token)) {
            throw new RuntimeException("回调令牌无效");
        }
        Channel channel = channelRepository.findByIdAndDeleted(job.getChannelId(), ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
        String normalizedChannelCode = normalizeLower(channelCode);
        if (normalizedChannelCode == null || !normalizedChannelCode.equals(channel.getCode())) {
            throw new RuntimeException("回调渠道不匹配");
        }
        return job;
    }

    private void fillFromRequest(ReleaseJob job, ReleaseJobCreateRequest request) {
        if (request == null) {
            throw new RuntimeException("请求不能为空");
        }
        job.setSourceReleaseBranch(requireBranch(request.getSourceReleaseBranch(), "源发布分支不能为空"));
        job.setChannelReleaseBranch(requireBranch(request.getChannelReleaseBranch(), "渠道发布分支不能为空"));
        job.setAppVersionName(requireText(request.getAppVersionName(), "主APK版本名称不能为空"));
        job.setAppVersionCode(requirePositive(request.getAppVersionCode(), "主APK版本号必须大于0"));
        job.setEngineVersionName(requireText(request.getEngineVersionName(), "引擎版本名称不能为空"));
        job.setEngineVersionCode(requirePositive(request.getEngineVersionCode(), "引擎版本号必须大于0"));
        job.setAnnouncementContent(requireText(request.getAnnouncementContent(), "发布公告内容不能为空"));
    }

    private void recordArtifacts(ReleaseJob job, ReleaseJobCompleteRequest request) {
        if (request == null) {
            return;
        }
        if (hasText(request.getAppArtifactUrl())) {
            job.setAppArtifactUrl(request.getAppArtifactUrl());
        }
        if (hasText(request.getAppArtifactMd5())) {
            job.setAppArtifactMd5(requireHex(request.getAppArtifactMd5(), 32, "主APK MD5格式不正确"));
        }
        if (hasText(request.getAppArtifactSha256())) {
            job.setAppArtifactSha256(requireHex(request.getAppArtifactSha256(), 64, "主APK SHA-256格式不正确"));
        }
        if (request.getAppArtifactSize() != null) {
            job.setAppArtifactSize(requirePositiveLong(request.getAppArtifactSize(), "主APK文件大小必须大于0"));
        }
        if (hasText(request.getEngineArtifactUrl())) {
            job.setEngineArtifactUrl(request.getEngineArtifactUrl());
        }
        if (hasText(request.getEngineArtifactMd5())) {
            job.setEngineArtifactMd5(requireHex(request.getEngineArtifactMd5(), 32, "引擎APK MD5格式不正确"));
        }
        if (hasText(request.getEngineArtifactSha256())) {
            job.setEngineArtifactSha256(requireHex(request.getEngineArtifactSha256(), 64, "引擎APK SHA-256格式不正确"));
        }
        if (request.getEngineArtifactSize() != null) {
            job.setEngineArtifactSize(requirePositiveLong(request.getEngineArtifactSize(), "引擎APK文件大小必须大于0"));
        }
    }

    private void requireSuccessfulArtifacts(ReleaseJob job) {
        requireText(job.getAppArtifactUrl(), "主APK产物地址不能为空");
        requireText(job.getEngineArtifactUrl(), "引擎APK产物地址不能为空");
        if (!hasText(job.getAppArtifactMd5()) && !hasText(job.getAppArtifactSha256())) {
            throw new RuntimeException("主APK校验值不能为空");
        }
        if (!hasText(job.getEngineArtifactMd5()) && !hasText(job.getEngineArtifactSha256())) {
            throw new RuntimeException("引擎APK校验值不能为空");
        }
        requirePositiveLong(job.getAppArtifactSize(), "主APK文件大小必须大于0");
        requirePositiveLong(job.getEngineArtifactSize(), "引擎APK文件大小必须大于0");
    }

    private Channel resolveMutableChannel(Long channelId) {
        Long effectiveChannelId = channelId;
        if (effectiveChannelId == null) {
            effectiveChannelId = channelRepository.findByCodeAndDeleted(Channel.MAIN_CODE, ACTIVE)
                    .map(Channel::getId)
                    .orElseThrow(() -> new RuntimeException("默认渠道不存在"));
        }
        permissionService.requireActiveChannelForMutation(effectiveChannelId);
        return channelRepository.findByIdAndDeleted(effectiveChannelId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
    }

    private Long normalizeChannelId(Long channelId) {
        if (channelId == null) {
            return null;
        }
        channelRepository.findByIdAndDeleted(channelId, ACTIVE)
                .orElseThrow(() -> new RuntimeException("渠道不存在"));
        return channelId;
    }

    private void requireNoActiveJob(Long channelId) {
        if (releaseJobRepository.existsByChannelIdAndStatusInAndDeleted(channelId, ACTIVE_STATUSES, ACTIVE)) {
            throw new RuntimeException("该渠道已有待执行或运行中的发布任务");
        }
    }

    private void requireNoOtherActiveJob(Long channelId, Long jobId) {
        if (releaseJobRepository.countActiveJobsForChannelExcluding(ACTIVE, channelId, ACTIVE_STATUSES, jobId) > 0) {
            throw new RuntimeException("该渠道已有待执行或运行中的发布任务");
        }
    }

    private ReleaseJob findActiveJob(Long id) {
        if (id == null) {
            throw new RuntimeException("任务ID不能为空");
        }
        return releaseJobRepository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("发布任务不存在"));
    }

    private ReleaseJobResponse toResponse(ReleaseJob job) {
        Channel channel = job.getChannelId() == null
                ? null
                : channelRepository.findByIdAndDeleted(job.getChannelId(), ACTIVE).orElse(null);
        return toResponse(job, channel);
    }

    private ReleaseJobResponse toResponse(ReleaseJob job, Channel channel) {
        return ReleaseJobResponse.from(job, channel);
    }

    private void appendFailureLog(ReleaseJob job, ReleaseJobCompleteRequest request, String token) {
        if (request == null) {
            appendLog(job, "Release worker reported failure", token);
            return;
        }
        appendLog(job, request.getLogExcerpt(), token);
        if (hasText(request.getErrorMessage())) {
            appendLog(job, "Error: " + request.getErrorMessage(), token);
        }
    }

    private void appendLog(ReleaseJob job, String line, String token) {
        String sanitized = sanitizeLog(line, token);
        if (sanitized == null) {
            return;
        }
        String existing = job.getLogExcerpt();
        String combined = existing == null || existing.isBlank() ? sanitized : existing + "\n" + sanitized;
        if (combined.length() > LOG_EXCERPT_LIMIT) {
            combined = combined.substring(combined.length() - LOG_EXCERPT_LIMIT);
        }
        job.setLogExcerpt(combined);
    }

    private String sanitizeLog(String line, String token) {
        String value = normalize(line);
        if (value == null) {
            return null;
        }
        if (hasText(token)) {
            value = value.replace(token, "[REDACTED]");
        }
        value = value.replaceAll("(?i)(token=)[^\\s&]+", "$1[REDACTED]");
        value = value.replaceAll("(?i)(authorization:\\s*bearer\\s+)[^\\s]+", "$1[REDACTED]");
        return value;
    }

    private boolean isSuccess(ReleaseJobCompleteRequest request) {
        if (request == null) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getSuccess())) {
            return true;
        }
        String status = normalizeUpper(request.getStatus());
        return ReleaseJob.STATUS_SUCCESS.equals(status);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("回调令牌生成失败");
        }
    }

    private boolean matchesToken(String expectedHash, String token) {
        if (!hasText(expectedHash) || !hasText(token)) {
            return false;
        }
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hashToken(token).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String requireBranch(String value, String message) {
        String branch = requireText(value, message);
        if (!branch.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
                || branch.contains("..")
                || branch.contains("//")
                || branch.endsWith("/")
                || branch.endsWith(".")) {
            throw new RuntimeException("发布分支格式不正确");
        }
        return branch;
    }

    private String requireHex(String value, int length, String message) {
        String normalized = requireText(value, message).toLowerCase(Locale.ROOT);
        if (normalized.length() != length || !normalized.matches("[0-9a-f]+")) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private Integer requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new RuntimeException(message);
        }
        return value;
    }

    private Long requirePositiveLong(Long value, String message) {
        if (value == null || value <= 0) {
            throw new RuntimeException(message);
        }
        return value;
    }

    private String primaryChecksum(String sha256, String md5) {
        return hasText(sha256) ? sha256 : md5;
    }

    private String normalizeStatusFilter(String status) {
        String normalized = normalizeUpper(status);
        if (normalized == null) {
            return null;
        }
        if (!ACTIVE_STATUSES.contains(normalized) && !TERMINAL_STATUSES.contains(normalized)) {
            throw new RuntimeException("任务状态不正确");
        }
        return normalized;
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstText(String first, String fallback) {
        return hasText(first) ? first.trim() : fallback;
    }

    private String normalizeBackendBaseUrl() {
        String normalized = normalize(backendBaseUrl);
        if (normalized == null) {
            return "";
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String callbackUrl(String path) {
        String baseUrl = normalizeBackendBaseUrl();
        return baseUrl.isBlank() ? path : baseUrl + path;
    }

    private String safeError(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private int pageNumber(Integer page) {
        return Math.max(1, page == null ? 1 : page);
    }

    private int pageSize(Integer size) {
        return Math.max(1, Math.min(100, size == null ? 10 : size));
    }
}
