package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.controller.dto.ReleaseJobCompleteRequest;
import com.duodian.admin.controller.dto.ReleaseJobCreateRequest;
import com.duodian.admin.controller.dto.ReleaseJobProgressRequest;
import com.duodian.admin.controller.dto.ReleaseJobResponse;
import com.duodian.admin.service.ChannelScopeService;
import com.duodian.admin.service.ReleaseJobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/release-jobs")
public class ReleaseJobController {
    private final ReleaseJobService releaseJobService;

    public ReleaseJobController(ReleaseJobService releaseJobService) {
        this.releaseJobService = releaseJobService;
    }

    @GetMapping
    public ApiResponse<PagedResponse<ReleaseJobResponse>> list(
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(releaseJobService.list(channelId, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReleaseJobResponse> get(@PathVariable Long id) {
        return ApiResponse.success(releaseJobService.get(id));
    }

    @PostMapping
    public ApiResponse<ReleaseJobResponse> create(@Valid @RequestBody ReleaseJobCreateRequest request) {
        return ApiResponse.success(releaseJobService.create(request));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<ReleaseJobResponse> start(@PathVariable Long id) {
        return ApiResponse.success(releaseJobService.start(id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<ReleaseJobResponse> retry(@PathVariable Long id) {
        return ApiResponse.success(releaseJobService.retry(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ReleaseJobResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(releaseJobService.cancel(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        releaseJobService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/callback/progress")
    public ApiResponse<ReleaseJobResponse> progress(
            @PathVariable Long id,
            @RequestHeader(value = ReleaseJobService.CALLBACK_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = ChannelScopeService.APK_CHANNEL_HEADER, required = false) String channelCode,
            @RequestBody(required = false) ReleaseJobProgressRequest request
    ) {
        return ApiResponse.success(releaseJobService.progress(id, token, channelCode, request));
    }

    @PostMapping("/{id}/callback/complete")
    public ApiResponse<ReleaseJobResponse> complete(
            @PathVariable Long id,
            @RequestHeader(value = ReleaseJobService.CALLBACK_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = ChannelScopeService.APK_CHANNEL_HEADER, required = false) String channelCode,
            @RequestBody(required = false) ReleaseJobCompleteRequest request
    ) {
        return ApiResponse.success(releaseJobService.complete(id, token, channelCode, request));
    }
}
