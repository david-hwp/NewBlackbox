package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.entity.Announcement;
import com.duodian.admin.repository.AnnouncementRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/announcements")
public class AnnouncementController {

    private final AnnouncementRepository repository;

    public AnnouncementController(AnnouncementRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<Announcement>> list(@RequestParam(required = false) Boolean published) {
        if (published != null) {
            return ApiResponse.success(repository.findByPublishedOrderByCreatedAtDesc(published));
        }
        return ApiResponse.success(repository.findAll());
    }

    @PostMapping
    public ApiResponse<Announcement> create(@RequestBody Announcement announcement) {
        return ApiResponse.success(repository.save(announcement));
    }

    @PutMapping("/{id}")
    public ApiResponse<Announcement> update(@PathVariable Long id, @RequestBody Announcement announcement) {
        Announcement existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("公告不存在"));
        existing.setTitle(announcement.getTitle());
        existing.setContent(announcement.getContent());
        existing.setPublished(announcement.getPublished());
        return ApiResponse.success(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ApiResponse.success();
    }
}
