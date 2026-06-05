package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.config.FileStorageProperties;
import com.duodian.admin.service.FileStorageService;
import com.duodian.admin.service.FileStorageService.DownloadedFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;

    public FileController(FileStorageService fileStorageService, FileStorageProperties fileStorageProperties) {
        this.fileStorageService = fileStorageService;
        this.fileStorageProperties = fileStorageProperties;
    }

    @PostMapping("/{type}")
    public ApiResponse<Map<String, String>> upload(@PathVariable String type, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("文件不能为空");
        }
        String url = fileStorageService.save(file, type);
        return ApiResponse.success(Map.of("url", url));
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, String>> config() {
        return ApiResponse.success(Map.of(
                "type", safeValue(fileStorageProperties.getType()),
                "localRoot", fileStorageService.resolveRoot().toString(),
                "endpoint", safeValue(fileStorageProperties.getEndpoint()),
                "bucket", safeValue(fileStorageProperties.getBucket()),
                "region", safeValue(fileStorageProperties.getRegion()),
                "publicBaseUrl", safeValue(fileStorageProperties.getPublicBaseUrl())
        ));
    }

    @GetMapping("/{type}/{fileName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String type, @PathVariable String fileName) throws MalformedURLException {
        if (!fileStorageService.isLocalStorage()) {
            DownloadedFile objectFile = fileStorageService.downloadObject(type, fileName);
            if (objectFile == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, objectFile.getContentType())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(new org.springframework.core.io.ByteArrayResource(objectFile.getBytes()));
        }

        Path path;
        try {
            path = fileStorageService.resolveFile(type, fileName);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        String contentType;
        try {
            contentType = Files.probeContentType(path);
        } catch (Exception e) {
            contentType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
