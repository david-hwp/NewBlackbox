package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PagedResponse;
import com.duodian.admin.entity.Announcement;
import com.duodian.admin.repository.AnnouncementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/announcements")
public class AnnouncementController {
    private static final byte ACTIVE = 0;
    private static final byte DELETED = 1;
    private static final String APP_RELEASE_TYPE = "APP_RELEASE";
    private static final String APP_RELEASE_TITLE = "新版本发布";

    private final AnnouncementRepository repository;

    public AnnouncementController(AnnouncementRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        String normalizedType = normalizeType(type);
        if (page != null || size != null || hasText(title)) {
            Page<Announcement> announcements = repository.searchAnnouncements(
                    ACTIVE,
                    normalize(title),
                    normalizedType,
                    published,
                    PageRequest.of(pageNumber(page) - 1, pageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            return ApiResponse.success(PagedResponse.from(announcements));
        }
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
        normalizeReleaseTitle(announcement);
        return ApiResponse.success(repository.save(announcement));
    }

    @PutMapping("/{id}")
    public ApiResponse<Announcement> update(@PathVariable Long id, @RequestBody Announcement announcement) {
        Announcement existing = repository.findByIdAndDeleted(id, ACTIVE)
                .orElseThrow(() -> new RuntimeException("公告不存在"));
        existing.setTitle(announcement.getTitle());
        existing.setContent(announcement.getContent());
        existing.setType(announcement.getType());
        normalizeReleaseTitle(existing);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int pageNumber(Integer page) {
        return Math.max(1, page == null ? 1 : page);
    }

    private int pageSize(Integer size) {
        return Math.max(1, Math.min(100, size == null ? 10 : size));
    }

    private void normalizeReleaseTitle(Announcement announcement) {
        if (announcement != null && APP_RELEASE_TYPE.equalsIgnoreCase(announcement.getType())) {
            announcement.setTitle(APP_RELEASE_TITLE);
        }
    }
}
