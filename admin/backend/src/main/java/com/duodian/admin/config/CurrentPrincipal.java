package com.duodian.admin.config;

public class CurrentPrincipal {
    private final Long userId;
    private final String role;
    private final Long channelId;
    private final String channelCode;
    private final String apkChannel;
    private final String loginSource;

    public CurrentPrincipal(
            Long userId,
            String role,
            Long channelId,
            String channelCode,
            String apkChannel,
            String loginSource
    ) {
        this.userId = userId;
        this.role = normalizeRole(role);
        this.channelId = channelId;
        this.channelCode = channelCode;
        this.apkChannel = apkChannel;
        this.loginSource = loginSource;
    }

    public static CurrentPrincipal userIdOnly(Long userId) {
        return new CurrentPrincipal(userId, null, null, null, null, "legacy");
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public Long getChannelId() {
        return channelId;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public String getApkChannel() {
        return apkChannel;
    }

    public String getLoginSource() {
        return loginSource;
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role) || "ADMIN".equals(role);
    }

    public boolean isChannelAdmin() {
        return "CHANNEL".equals(role);
    }

    public boolean isUser() {
        return "USER".equals(role);
    }

    public boolean isAdminRole() {
        return isSuperAdmin() || isChannelAdmin();
    }

    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        return "ADMIN".equals(normalized) ? "SUPER_ADMIN" : normalized;
    }
}
