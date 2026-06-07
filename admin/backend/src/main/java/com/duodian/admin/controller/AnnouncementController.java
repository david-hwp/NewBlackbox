package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Announcement;
import com.duodian.admin.repository.AnnouncementRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/announcements")
public class AnnouncementController {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;

    private final AnnouncementRepository repository;

    public AnnouncementController(AnnouncementRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<Announcement>> list(
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String type
    ) {
        String normalizedType = normalizeType(type);
        if (published != null && normalizedType != null) {
            return ApiResponse.success(repository.findByPublishedAndTypeAndDeletedOrderByCreatedAtDesc(published, normalizedType, ACTIVE));
        }
        if (published != null) {
            return ApiResponse.success(repository.findByPublishedAndDeletedOrderByCreatedAtDesc(published, ACTIVE));
        }
        if (normalizedType != null) {
            return ApiResponse.success(repository.findByTypeAndDeletedOrderByCreatedAtDesc(normalizedType, ACTIVE));
        }
        return ApiResponse.success(repository.findByDeletedOrderByCreatedAtDesc(ACTIVE));
    }

    @PostMapping
    public ApiResponse<Announcement> create(@RequestBody Announcement announcement) {
        announcement.setDeleted(ACTIVE);
        return ApiResponse.success(repository.save(announcement));
    }

    @PutMapping("/{id}")
    public ApiResponse<Announcement> update(@PathVariable Long id, @RequestBody Announcement announcement) {
        Announcement existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("公告不存在"));
        existing.setTitle(announcement.getTitle());
        existing.setContent(announcement.getContent());
        existing.setType(announcement.getType());
        existing.setPublished(announcement.getPublished());
        return ApiResponse.success(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Announcement existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("公告不存在"));
        existing.setDeleted(DELETED);
        repository.save(existing);
        return ApiResponse.success();
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.trim();
    }
}
