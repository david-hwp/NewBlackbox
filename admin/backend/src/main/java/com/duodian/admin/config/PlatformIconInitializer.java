package com.duodian.admin.config;

import com.duodian.admin.service.FileStorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class PlatformIconInitializer implements CommandLineRunner {

    private final FileStorageService fileStorageService;

    private static final Map<String, Path> ICONS = Map.of(
            "jd.png", Path.of("/app/platform-icons/jd.png"),
            "meituan.png", Path.of("/app/platform-icons/meituan.png"),
            "qianniu.png", Path.of("/app/platform-icons/qianniu.png"),
            "kuaishou.png", Path.of("/app/platform-icons/kuaishou.png"),
            "xiaohongshu.png", Path.of("/app/platform-icons/xiaohongshu.png"),
            "koubei.png", Path.of("/app/platform-icons/koubei.png")
    );

    public PlatformIconInitializer(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!fileStorageService.isLocalStorage()) {
            return;
        }
        Path targetDir = fileStorageService.resolveRoot().resolve("platform-icons");
        Files.createDirectories(targetDir);
        for (Map.Entry<String, Path> entry : ICONS.entrySet()) {
            Path target = targetDir.resolve(entry.getKey());
            if (Files.exists(target)) {
                continue;
            }
            copyIcon(entry.getValue(), target);
        }
    }

    private void copyIcon(Path source, Path target) throws IOException {
        Path absoluteSource = source.toAbsolutePath().normalize();
        if (Files.exists(absoluteSource)) {
            Files.copy(absoluteSource, target);
            return;
        }
        Path localSource = Path.of("app/src/main/res/drawable-xhdpi").resolve(source.getFileName()).toAbsolutePath().normalize();
        if (Files.exists(localSource)) {
            Files.copy(localSource, target);
        }
    }
}
