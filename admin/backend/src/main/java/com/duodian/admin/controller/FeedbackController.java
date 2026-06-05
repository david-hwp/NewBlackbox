package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Feedback;
import com.duodian.admin.entity.User;
import com.duodian.admin.service.FeedbackService;
import com.duodian.admin.service.FileStorageService;
import com.duodian.admin.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/feedbacks")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    private static final long MAX_IMAGE_SIZE = 1024 * 1024; // 1MB
    private static final long MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_LOG_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_IMAGES = 4;

    public FeedbackController(FeedbackService feedbackService, UserService userService, FileStorageService fileStorageService) {
        this.feedbackService = feedbackService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public ApiResponse<List<Feedback>> list(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ApiResponse.success(feedbackService.findByStatus(status));
        }
        return ApiResponse.success(feedbackService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Feedback> get(@PathVariable Long id) {
        return feedbackService.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("反馈不存在"));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Feedback> updateStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String status = request.get("status");
        if (status == null || status.isBlank()) {
            return ApiResponse.error("状态不能为空");
        }
        return ApiResponse.success(feedbackService.updateStatus(id, status));
    }

    @PostMapping
    public ApiResponse<Feedback> create(
            @RequestParam("content") String content,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestParam(value = "logFile", required = false) MultipartFile logFile) {

        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }

        if (content == null || content.isBlank()) {
            return ApiResponse.error("反馈内容不能为空");
        }

        User user = userService.findById(userId).orElse(null);

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setUserPhone(user != null ? user.getPhone() : null);
        feedback.setContent(content);

        // Handle images
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            if (images.size() > MAX_IMAGES) {
                return ApiResponse.error("最多上传4张图片");
            }
            for (MultipartFile image : images) {
                if (!image.isEmpty()) {
                    if (!ALLOWED_IMAGE_TYPES.contains(image.getContentType())) {
                        return ApiResponse.error("图片格式仅支持 jpg/png/webp");
                    }
                    if (image.getSize() > MAX_IMAGE_SIZE) {
                        return ApiResponse.error("单张图片不能超过1MB");
                    }
                    String url = fileStorageService.save(image, "feedback-images");
                    imageUrls.add(url);
                }
            }
        }
        if (!imageUrls.isEmpty()) {
            feedback.setImageUrls(String.join(",", imageUrls));
        }

        List<String> attachmentUrls = new ArrayList<>();
        if (attachments != null && !attachments.isEmpty()) {
            for (MultipartFile attachment : attachments) {
                if (!attachment.isEmpty()) {
                    if (attachment.getSize() > MAX_ATTACHMENT_SIZE) {
                        return ApiResponse.error("单个附件不能超过10MB");
                    }
                    String url = fileStorageService.save(attachment, "feedback-attachments");
                    attachmentUrls.add(url);
                }
            }
        }
        if (!attachmentUrls.isEmpty()) {
            feedback.setAttachmentUrls(String.join(",", attachmentUrls));
        }

        // Handle log file
        if (logFile != null && !logFile.isEmpty()) {
            if (logFile.getSize() > MAX_LOG_SIZE) {
                return ApiResponse.error("日志文件不能超过10MB");
            }
            String logUrl = fileStorageService.save(logFile, "feedback-logs");
            feedback.setLogUrl(logUrl);
        }

        Feedback saved = feedbackService.create(feedback);
        return ApiResponse.success(saved);
    }
}
