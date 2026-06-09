package com.duodian.admin.service;

import com.duodian.admin.config.CurrentPrincipal;
import com.duodian.admin.controller.dto.ReleaseJobCompleteRequest;
import com.duodian.admin.controller.dto.ReleaseJobCreateRequest;
import com.duodian.admin.controller.dto.ReleaseJobProgressRequest;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.ReleaseJob;
import com.duodian.admin.repository.AnnouncementRepository;
import com.duodian.admin.repository.AppVersionRepository;
import com.duodian.admin.repository.ChannelRepository;
import com.duodian.admin.repository.EngineVersionRepository;
import com.duodian.admin.repository.ReleaseJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class ReleaseJobServiceTest {
    private static final byte ACTIVE = 0;

    private final ReleaseJobRepository releaseJobRepository = mock(ReleaseJobRepository.class);
    private final ChannelRepository channelRepository = mock(ChannelRepository.class);
    private final AppVersionRepository appVersionRepository = mock(AppVersionRepository.class);
    private final EngineVersionRepository engineVersionRepository = mock(EngineVersionRepository.class);
    private final AnnouncementRepository announcementRepository = mock(AnnouncementRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final CapturingReleaseJobRunner runner = new CapturingReleaseJobRunner();
    private final ReleaseJobService service = new ReleaseJobService(
            releaseJobRepository,
            channelRepository,
            appVersionRepository,
            engineVersionRepository,
            announcementRepository,
            permissionService,
            runner
    );

    private Channel channel;

    @BeforeEach
    void setUp() {
        channel = channel(2L, "beta");
        when(permissionService.currentPrincipal()).thenReturn(new CurrentPrincipal(1L, "SUPER_ADMIN", 1L, "main", "main", "test"));
        when(channelRepository.findByIdAndDeleted(2L, ACTIVE)).thenReturn(Optional.of(channel));
        when(channelRepository.findByCodeAndDeleted("main", ACTIVE)).thenReturn(Optional.of(channel(1L, "main")));
    }

    @Test
    void createRequiresSuperAdminAndDoesNotCreateForChannelUser() {
        doThrow(new RuntimeException("无权限")).when(permissionService).requireSuperAdmin();

        assertThatThrownBy(() -> service.create(createRequest()))
                .hasMessage("无权限");

        verify(releaseJobRepository, never()).save(any());
    }

    @Test
    void startStoresOnlyTokenHashAndRunnerReceivesRawToken() {
        ReleaseJob job = job(10L, ReleaseJob.STATUS_PENDING);
        when(releaseJobRepository.findByIdAndDeleted(10L, ACTIVE)).thenReturn(Optional.of(job));
        when(releaseJobRepository.countActiveJobsForChannelExcluding(eq(ACTIVE), eq(2L), anyStatusCollection(), eq(10L)))
                .thenReturn(0L);
        when(releaseJobRepository.save(any(ReleaseJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.start(10L);

        assertThat(job.getStatus()).isEqualTo(ReleaseJob.STATUS_RUNNING);
        assertThat(job.getCallbackTokenHash()).hasSize(64);
        assertThat(runner.context).isNotNull();
        assertThat(runner.context.callbackToken()).isNotBlank();
        assertThat(job.getCallbackTokenHash()).doesNotContain(runner.context.callbackToken());
        assertThat(job.getLogExcerpt()).doesNotContain(runner.context.callbackToken());
        assertThat(runner.context.channelCode()).isEqualTo("beta");
        assertThat(runner.context.backendBaseUrl()).isEmpty();
        assertThat(runner.context.progressCallbackUrl()).isEqualTo("/api/release-jobs/10/callback/progress");
    }

    @Test
    void progressRejectsWrongTokenAndWrongChannel() {
        ReleaseJob job = runningJobWithToken("secret-token");
        when(releaseJobRepository.findByIdAndDeleted(10L, ACTIVE)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.progress(10L, "bad-token", "beta", progressRequest()))
                .hasMessage("回调令牌无效");

        assertThatThrownBy(() -> service.progress(10L, "secret-token", "main", progressRequest()))
                .hasMessage("回调渠道不匹配");
    }

    @Test
    void failedCallbackDoesNotPublishVersionsOrAnnouncement() {
        ReleaseJob job = runningJobWithToken("secret-token");
        when(releaseJobRepository.findByIdAndDeleted(10L, ACTIVE)).thenReturn(Optional.of(job));
        when(releaseJobRepository.save(any(ReleaseJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReleaseJobCompleteRequest request = new ReleaseJobCompleteRequest();
        request.setSuccess(false);
        request.setProgress(67);
        request.setLogExcerpt("failed token=secret-token");
        request.setErrorMessage("build failed");

        service.complete(10L, "secret-token", "beta", request);

        assertThat(job.getStatus()).isEqualTo(ReleaseJob.STATUS_FAILED);
        assertThat(job.getProgress()).isEqualTo(67);
        assertThat(job.getLogExcerpt()).contains("token=[REDACTED]");
        assertThat(job.getLogExcerpt()).doesNotContain("secret-token");
        verify(appVersionRepository, never()).save(any());
        verify(engineVersionRepository, never()).save(any());
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void successfulCallbackPublishesChannelScopedVersionsAndReleaseAnnouncement() {
        ReleaseJob job = runningJobWithToken("secret-token");
        when(releaseJobRepository.findByIdAndDeleted(10L, ACTIVE)).thenReturn(Optional.of(job));
        when(releaseJobRepository.save(any(ReleaseJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appVersionRepository.findByChannelIdAndVersionCodeAndDeleted(2L, 120, ACTIVE)).thenReturn(Optional.empty());
        when(engineVersionRepository.findByChannelIdAndVersionCodeAndDeleted(2L, 220, ACTIVE)).thenReturn(Optional.empty());
        when(announcementRepository.findFirstByChannelIdAndTypeAndDeletedOrderByCreatedAtDesc(2L, "APP_RELEASE", ACTIVE))
                .thenReturn(Optional.empty());

        ReleaseJobCompleteRequest request = successRequest();

        service.complete(10L, "secret-token", "beta", request);

        assertThat(job.getStatus()).isEqualTo(ReleaseJob.STATUS_SUCCESS);
        assertThat(job.getProgress()).isEqualTo(100);
        verify(appVersionRepository).save(argThat(version ->
                version.getChannelId().equals(2L)
                        && version.getVersionCode().equals(120)
                        && "1.2.0".equals(version.getVersionName())
                        && "/api/files/app-packages/app.apk".equals(version.getApkUrl())
                        && version.getChecksum().equals(request.getAppArtifactSha256())
                        && version.getFileSize().equals(12345L)
                        && Boolean.TRUE.equals(version.getPublished())
        ));
        verify(engineVersionRepository).save(argThat(version ->
                version.getChannelId().equals(2L)
                        && version.getVersionCode().equals(220)
                        && "2.2.0".equals(version.getVersionName())
                        && "/api/files/engine-packages/engine.apk".equals(version.getApkUrl())
                        && version.getChecksum().equals(request.getEngineArtifactSha256())
                        && Boolean.TRUE.equals(version.getAvailable())
        ));
        verify(announcementRepository).save(argThat(announcement ->
                announcement.getChannelId().equals(2L)
                        && "APP_RELEASE".equals(announcement.getType())
                        && "新版本发布".equals(announcement.getTitle())
                        && "发布说明".equals(announcement.getContent())
                        && Boolean.TRUE.equals(announcement.getPublished())
        ));
    }

    private ReleaseJobCreateRequest createRequest() {
        ReleaseJobCreateRequest request = new ReleaseJobCreateRequest();
        request.setChannelId(2L);
        request.setSourceReleaseBranch("release/source");
        request.setChannelReleaseBranch("release/beta");
        request.setAppVersionName("1.2.0");
        request.setAppVersionCode(120);
        request.setEngineVersionName("2.2.0");
        request.setEngineVersionCode(220);
        request.setAnnouncementContent("发布说明");
        return request;
    }

    private ReleaseJobProgressRequest progressRequest() {
        ReleaseJobProgressRequest request = new ReleaseJobProgressRequest();
        request.setProgress(25);
        request.setLogExcerpt("building");
        return request;
    }

    private ReleaseJobCompleteRequest successRequest() {
        ReleaseJobCompleteRequest request = new ReleaseJobCompleteRequest();
        request.setSuccess(true);
        request.setAppArtifactUrl("/api/files/app-packages/app.apk");
        request.setAppArtifactMd5("0123456789abcdef0123456789abcdef");
        request.setAppArtifactSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        request.setAppArtifactSize(12345L);
        request.setEngineArtifactUrl("/api/files/engine-packages/engine.apk");
        request.setEngineArtifactMd5("abcdef0123456789abcdef0123456789");
        request.setEngineArtifactSha256("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        request.setEngineArtifactSize(23456L);
        request.setLogExcerpt("complete");
        return request;
    }

    private ReleaseJob runningJobWithToken(String token) {
        ReleaseJob job = job(10L, ReleaseJob.STATUS_RUNNING);
        job.setCallbackTokenHash(sha256(token));
        return job;
    }

    private ReleaseJob job(Long id, String status) {
        ReleaseJob job = new ReleaseJob();
        job.setId(id);
        job.setChannelId(2L);
        job.setSourceReleaseBranch("release/source");
        job.setChannelReleaseBranch("release/beta");
        job.setAppVersionName("1.2.0");
        job.setAppVersionCode(120);
        job.setEngineVersionName("2.2.0");
        job.setEngineVersionCode(220);
        job.setAnnouncementContent("发布说明");
        job.setStatus(status);
        job.setProgress(0);
        job.setDeleted(ACTIVE);
        return job;
    }

    private Channel channel(Long id, String code) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setCode(code);
        channel.setName(code + "渠道");
        channel.setStatus(Channel.STATUS_ACTIVE);
        channel.setAppApplicationId("com.example." + code);
        channel.setEngineApplicationId("com.example." + code + ".engine");
        channel.setDeleted(ACTIVE);
        return channel;
    }

    private String sha256(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<String> anyStatusCollection() {
        return any(Set.class);
    }

    private static class CapturingReleaseJobRunner implements ReleaseJobRunner {
        private ReleaseJobRunContext context;

        @Override
        public void start(ReleaseJobRunContext context) {
            this.context = context;
        }
    }
}
