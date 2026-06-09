package com.duodian.admin.service;

public record ReleaseJobRunContext(
        Long jobId,
        String channelCode,
        String appApplicationId,
        String engineApplicationId,
        String appDisplayName,
        String engineDisplayName,
        String sourceReleaseBranch,
        String channelReleaseBranch,
        String appVersionName,
        Integer appVersionCode,
        String engineVersionName,
        Integer engineVersionCode,
        String backendBaseUrl,
        String progressCallbackUrl,
        String completeCallbackUrl,
        String callbackToken
) {
}
