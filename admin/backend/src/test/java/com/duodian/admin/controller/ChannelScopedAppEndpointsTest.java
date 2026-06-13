package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.config.CurrentPrincipal;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Announcement;
import com.duodian.admin.entity.AppVersion;
import com.duodian.admin.entity.Channel;
import com.duodian.admin.entity.EngineVersion;
import com.duodian.admin.repository.AnnouncementRepository;
import com.duodian.admin.repository.AppVersionRepository;
import com.duodian.admin.repository.EngineVersionRepository;
import com.duodian.admin.service.ChannelScopeService;
import com.duodian.admin.service.PackageIntegrityService;
import com.duodian.admin.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelScopedAppEndpointsTest {
    private static final byte ACTIVE = 0;

    private final AppVersionRepository appVersionRepository = mock(AppVersionRepository.class);
    private final EngineVersionRepository engineVersionRepository = mock(EngineVersionRepository.class);
    private final AnnouncementRepository announcementRepository = mock(AnnouncementRepository.class);
    private final PackageIntegrityService packageIntegrityService = mock(PackageIntegrityService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final ChannelScopeService channelScopeService = mock(ChannelScopeService.class);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void appVersionListUsesApkChannelHeaderEvenWhenTokenBelongsToSuperAdmin() {
        HttpServletRequest request = appRequest();
        Channel beta = channel(2L, "beta");
        when(channelScopeService.hasAppChannelHeader(request)).thenReturn(true);
        when(channelScopeService.resolveAppChannel(request, null)).thenReturn(beta);
        when(appVersionRepository.findByChannelIdAndPublishedAndDeletedOrderByVersionCodeDesc(2L, true, ACTIVE))
                .thenReturn(List.of(appVersion(2L, 200)));

        AuthContext.setPrincipal(new CurrentPrincipal(1L, "SUPER_ADMIN", 1L, "main", "main", "jwt"));
        AppVersionController controller = new AppVersionController(
                appVersionRepository,
                packageIntegrityService,
                permissionService,
                channelScopeService
        );

        ApiResponse<?> response = controller.list(true, null, null, null, null, null, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat((List<?>) response.getData()).hasSize(1);
        verify(channelScopeService).requireActiveForApp(beta);
        verify(permissionService, never()).filterChannelForQuery(null);
    }

    @Test
    void engineVersionListUsesApkChannelHeaderEvenWhenTokenBelongsToSuperAdmin() {
        HttpServletRequest request = appRequest();
        Channel beta = channel(2L, "beta");
        when(channelScopeService.hasAppChannelHeader(request)).thenReturn(true);
        when(channelScopeService.resolveAppChannel(request, null)).thenReturn(beta);
        when(engineVersionRepository.findByChannelIdAndAvailableAndDeletedOrderByVersionCodeDesc(2L, true, ACTIVE))
                .thenReturn(List.of(engineVersion(2L, 200)));

        AuthContext.setPrincipal(new CurrentPrincipal(1L, "SUPER_ADMIN", 1L, "main", "main", "jwt"));
        EngineVersionController controller = new EngineVersionController(
                engineVersionRepository,
                packageIntegrityService,
                permissionService,
                channelScopeService
        );

        ApiResponse<?> response = controller.list(true, null, null, null, null, null, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat((List<?>) response.getData()).hasSize(1);
        verify(channelScopeService).requireActiveForApp(beta);
        verify(permissionService, never()).filterChannelForQuery(null);
    }

    @Test
    void announcementListUsesApkChannelHeaderEvenWhenTokenBelongsToSuperAdmin() {
        HttpServletRequest request = appRequest();
        Channel beta = channel(2L, "beta");
        when(channelScopeService.hasAppChannelHeader(request)).thenReturn(true);
        when(channelScopeService.resolveAppChannel(request, null)).thenReturn(beta);
        when(announcementRepository.findByChannelIdAndPublishedAndTypeAndDeletedOrderByCreatedAtDesc(
                eq(2L),
                eq(true),
                eq("APP_RELEASE"),
                eq(ACTIVE)
        )).thenReturn(List.of(announcement(2L)));

        AuthContext.setPrincipal(new CurrentPrincipal(1L, "SUPER_ADMIN", 1L, "main", "main", "jwt"));
        AnnouncementController controller = new AnnouncementController(
                announcementRepository,
                permissionService,
                channelScopeService
        );

        ApiResponse<?> response = controller.list(true, "APP_RELEASE", null, null, null, null, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat((List<?>) response.getData()).hasSize(1);
        verify(channelScopeService).requireActiveForApp(beta);
        verify(permissionService, never()).filterChannelForQuery(null);
    }

    private HttpServletRequest appRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(ChannelScopeService.APK_CHANNEL_HEADER)).thenReturn("beta");
        return request;
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

    private AppVersion appVersion(Long channelId, int versionCode) {
        AppVersion version = new AppVersion();
        version.setChannelId(channelId);
        version.setVersionCode(versionCode);
        version.setVersionName("v" + versionCode);
        version.setApkUrl("/api/files/apk/test.apk");
        version.setApplicationId("com.example.app");
        version.setPublished(true);
        version.setDeleted(ACTIVE);
        return version;
    }

    private EngineVersion engineVersion(Long channelId, int versionCode) {
        EngineVersion version = new EngineVersion();
        version.setChannelId(channelId);
        version.setVersionCode(versionCode);
        version.setVersionName("v" + versionCode);
        version.setApkUrl("/api/files/engine-apk/test.apk");
        version.setApplicationId("com.example.engine");
        version.setAvailable(true);
        version.setDeleted(ACTIVE);
        return version;
    }

    private Announcement announcement(Long channelId) {
        Announcement announcement = new Announcement();
        announcement.setChannelId(channelId);
        announcement.setTitle("新版本发布");
        announcement.setType("APP_RELEASE");
        announcement.setContent("content");
        announcement.setPublished(true);
        announcement.setDeleted(ACTIVE);
        return announcement;
    }
}
