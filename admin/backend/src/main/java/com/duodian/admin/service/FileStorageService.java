package com.duodian.admin.service;

import com.duodian.admin.config.FileStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class FileStorageService {

    public static final String THUMBNAIL_SUFFIX = ".thumb.jpg";
    private static final int THUMBNAIL_MAX_SIZE = 240;

    private final FileStorageProperties properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public String save(MultipartFile file, String type) {
        try {
            String safeType = safeType(type);
            String fileName = createFileName(file.getOriginalFilename());
            byte[] body = file.getBytes();
            String contentType = file.getContentType();
            byte[] thumbnail = createThumbnail(body, contentType, fileName);
            String thumbnailFileName = thumbnailFileName(fileName);
            if (isLocalStorage()) {
                Path dir = resolveRoot().resolve(safeType);
                Files.createDirectories(dir);
                Path target = dir.resolve(fileName);
                Files.write(target, body);
                if (thumbnail != null) {
                    Files.write(dir.resolve(thumbnailFileName), thumbnail);
                }
                return createDownloadUrl(safeType, fileName);
            }
            uploadObject(safeType + "/" + fileName, body, contentType);
            if (thumbnail != null) {
                uploadObject(safeType + "/" + thumbnailFileName, thumbnail, "image/jpeg");
            }
            return createDownloadUrl(safeType, fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage());
        }
    }

    public DownloadedFile downloadObject(String type, String fileName) {
        String key = safeType(type) + "/" + fileName;
        try {
            HttpResponse<byte[]> response = sendObjectRequest("GET", key, null, null);
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("对象存储下载失败: HTTP " + response.statusCode() + " " + responseBody(response));
            }
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            return new DownloadedFile(response.body(), contentType);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("对象存储下载失败: " + e.getMessage());
        }
    }

    public Path resolveFile(String type, String fileName) {
        Path dir = resolveRoot().resolve(safeType(type)).normalize();
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return file;
    }

    public Path resolveRoot() {
        String root = properties.getLocalRoot();
        if (root.startsWith("~/")) {
            root = System.getProperty("user.home") + root.substring(1);
        }
        return Path.of(root).toAbsolutePath().normalize();
    }

    public boolean isLocalStorage() {
        return "local".equalsIgnoreCase(properties.getType());
    }

    public String createDownloadUrl(String type, String fileName) {
        String safeType = safeType(type);
        return "/api/files/" + safeType + "/" + fileName;
    }

    public String createThumbnailDownloadUrl(String type, String fileName) {
        return createDownloadUrl(type, thumbnailFileName(fileName));
    }

    private String createFileName(String originalName) {
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return timestamp + "-" + UUID.randomUUID().toString().replace("-", "") + ext;
    }

    private String safeType(String type) {
        if (type == null || type.isBlank()) {
            return "other";
        }
        return type.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private void uploadObject(String key, byte[] body, String contentType) throws IOException {
        try {
            HttpResponse<byte[]> response = sendObjectRequest("PUT", key, contentType, body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("对象存储上传失败: HTTP " + response.statusCode() + " " + responseBody(response));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("对象存储上传被中断");
        }
    }

    private HttpResponse<byte[]> sendObjectRequest(String method, String key, String contentType, byte[] body)
            throws IOException, InterruptedException {
        ensureObjectStorageConfigured();
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = sha256Hex(payload);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        URI uri = objectUri(key);
        String host = hostHeader(uri);
        String signedHeaders = contentType == null || contentType.isBlank()
                ? "host;x-amz-content-sha256;x-amz-date"
                : "content-type;host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders = canonicalHeaders(contentType, host, payloadHash, amzDate);
        String canonicalRequest = method + "\n" +
                uri.getRawPath() + "\n\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                payloadHash;
        String region = objectRegion();
        String scope = date + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" +
                amzDate + "\n" +
                scope + "\n" +
                sha256Hex(canonicalRequest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = hmacHex(signingKey(date, region), stringToSign);
        String authHeader = "AWS4-HMAC-SHA256 Credential=" + properties.getAccessKey() + "/" + scope +
                ",SignedHeaders=" + signedHeaders +
                ",Signature=" + signature;

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .version(HttpClient.Version.HTTP_1_1)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authHeader);
        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }
        if ("PUT".equalsIgnoreCase(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload));
        } else if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else {
            throw new IllegalArgumentException("Unsupported object storage method: " + method);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI objectUri(String key) {
        String endpoint = trimTrailingSlash(properties.getEndpoint());
        String bucket = properties.getBucket();
        if ("virtual-host".equalsIgnoreCase(properties.getAddressingStyle())) {
            URI endpointUri = URI.create(endpoint);
            String host = bucket + "." + endpointUri.getHost();
            String authority = endpointUri.getPort() == -1 ? host : host + ":" + endpointUri.getPort();
            String path = "/" + encodePath(key);
            return URI.create(endpointUri.getScheme() + "://" + authority + path);
        }
        String path = "/" + encodePath(bucket + "/" + key);
        return URI.create(endpoint + path);
    }

    private String canonicalHeaders(String contentType, String host, String payloadHash, String amzDate) {
        StringBuilder builder = new StringBuilder();
        if (contentType != null && !contentType.isBlank()) {
            builder.append("content-type:").append(contentType.trim()).append("\n");
        }
        builder.append("host:").append(host).append("\n");
        builder.append("x-amz-content-sha256:").append(payloadHash).append("\n");
        builder.append("x-amz-date:").append(amzDate).append("\n");
        return builder.toString();
    }

    private void ensureObjectStorageConfigured() {
        if (!isObjectStorage()) {
            throw new UnsupportedOperationException("不支持的文件存储类型: " + properties.getType());
        }
        if (isBlank(properties.getEndpoint()) || isBlank(properties.getBucket()) ||
                isBlank(properties.getAccessKey()) || isBlank(properties.getSecretKey())) {
            throw new IllegalStateException("对象存储 endpoint/bucket/accessKey/secretKey 必须完整配置");
        }
    }

    private boolean isObjectStorage() {
        String type = properties.getType();
        return "object".equalsIgnoreCase(type) ||
                "s3".equalsIgnoreCase(type) ||
                "minio".equalsIgnoreCase(type) ||
                "oss".equalsIgnoreCase(type) ||
                "obs".equalsIgnoreCase(type);
    }

    private String objectRegion() {
        return isBlank(properties.getRegion()) ? "us-east-1" : properties.getRegion();
    }

    private byte[] signingKey(String date, String region) {
        byte[] kDate = hmac(("AWS4" + properties.getSecretKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8), date);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, "s3");
        return hmac(kService, "aws4_request");
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("对象存储签名失败", e);
        }
    }

    private String hmacHex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmac(key, data));
    }

    private String encodePath(String path) {
        StringBuilder builder = new StringBuilder();
        for (byte b : path.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~' || c == '/') {
                builder.append(c);
            } else {
                builder.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xff));
            }
        }
        return builder.toString();
    }

    private String hostHeader(URI uri) {
        int port = uri.getPort();
        boolean defaultPort = port == -1 ||
                ("http".equalsIgnoreCase(uri.getScheme()) && port == 80) ||
                ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
        return defaultPort ? uri.getHost() : uri.getHost() + ":" + port;
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String responseBody(HttpResponse<byte[]> response) {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return "";
        }
        String text = new String(body, java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private byte[] createThumbnail(byte[] body, String contentType, String fileName) {
        if (!isImageFile(contentType, fileName)) {
            return null;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(body));
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
                return null;
            }
            double scale = Math.min(
                    1d,
                    Math.min((double) THUMBNAIL_MAX_SIZE / source.getWidth(), (double) THUMBNAIL_MAX_SIZE / source.getHeight())
            );
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(target, "jpg", output)) {
                return null;
            }
            return output.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isImageFile(String contentType, String fileName) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return !contentType.toLowerCase(Locale.ROOT).contains("svg");
        }
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp") ||
                lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".gif");
    }

    private String thumbnailFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file" + THUMBNAIL_SUFFIX;
        }
        if (fileName.endsWith(THUMBNAIL_SUFFIX)) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + THUMBNAIL_SUFFIX;
        }
        return fileName.substring(0, dot) + THUMBNAIL_SUFFIX;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class DownloadedFile {
        private final byte[] bytes;
        private final String contentType;

        public DownloadedFile(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
