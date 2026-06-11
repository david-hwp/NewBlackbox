package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

abstract class PatternFileShopIdExtractor implements ShopIdExtractor {

    private static final int MAX_FILE_BYTES = 768 * 1024;
    private static final int MAX_FILES = 600;
    private static final int MAX_DEPTH = 8;

    private static final Pattern JSON_NAME_VALUE_PATTERN = Pattern.compile(
            "(?is)(?:\\\\?[\\\"'])?(%s)(?:\\\\?[\\\"'])?\\s*[:=]\\s*(?:\\\\?[\\\"'])?([^\\\\\\\"'{}\\[\\],\\r\\n]{1,160})");
    private static final Pattern URL_NAME_VALUE_PATTERN = Pattern.compile(
            "(?is)(?:^|[?&;\\s])(%s)=([^&;\\s]{1,200})");
    private static final Pattern XML_NAME_VALUE_PATTERN = Pattern.compile(
            "(?i)<(?:string|int|long)\\s+name=\\\"(%s)\\\"(?:\\s+value=\\\"([^\\\"]+)\\\")?\\s*>([^<]*)</(?:string|int|long)>");
    private static final Pattern XML_SELF_CLOSING_NAME_VALUE_PATTERN = Pattern.compile(
            "(?i)<(?:string|int|long)\\s+name=\\\"(%s)\\\"\\s+value=\\\"([^\\\"]+)\\\"\\s*/>");

    private final String targetPackage;
    private final String platform;
    private final String tag;
    private final Pattern idKeyPattern;
    private final Pattern nameKeyPattern;
    private final Pattern idUrlPattern;
    private final Pattern nameUrlPattern;
    private final Pattern idKeyOnlyPattern;
    private final Pattern nameKeyOnlyPattern;

    PatternFileShopIdExtractor(
            String targetPackage,
            String platform,
            String tag,
            String idKeyAlternation,
            String nameKeyAlternation
    ) {
        this.targetPackage = targetPackage;
        this.platform = platform;
        this.tag = tag;
        this.idKeyPattern = Pattern.compile(String.format(JSON_NAME_VALUE_PATTERN.pattern(), idKeyAlternation));
        this.nameKeyPattern = Pattern.compile(String.format(JSON_NAME_VALUE_PATTERN.pattern(), nameKeyAlternation));
        this.idUrlPattern = Pattern.compile(String.format(URL_NAME_VALUE_PATTERN.pattern(), idKeyAlternation));
        this.nameUrlPattern = Pattern.compile(String.format(URL_NAME_VALUE_PATTERN.pattern(), nameKeyAlternation));
        this.idKeyOnlyPattern = Pattern.compile("(?is)(?:\\\\?[\\\"'])?(" + idKeyAlternation + ")(?:\\\\?[\\\"'])?");
        this.nameKeyOnlyPattern = Pattern.compile("(?is)(?:\\\\?[\\\"'])?(" + nameKeyAlternation + ")(?:\\\\?[\\\"'])?");
    }

    @Override
    public String getTargetPackage() {
        return targetPackage;
    }

    @Override
    public ShopInfo extract(Context context, int userId) {
        List<File> roots = collectRoots(userId);
        if (roots.isEmpty()) {
            return null;
        }
        for (File file : collectCandidateFiles(roots)) {
            ShopInfo info = extractFromFile(file);
            if (info != null) {
                Slog.d(tag, "Extracted verified shop identity from " + file.getAbsolutePath());
                return info;
            }
        }
        return null;
    }

    private List<File> collectRoots(int userId) {
        List<File> roots = new ArrayList<>();
        File dataDir = BEnvironment.getDataDir(targetPackage, userId);
        addIfExists(roots, new File(dataDir, "shared_prefs"));
        addIfExists(roots, new File(dataDir, "files"));
        addIfExists(roots, new File(dataDir, "cache"));
        addIfExists(roots, BEnvironment.getExternalDataFilesDir(targetPackage, userId));
        addIfExists(roots, BEnvironment.getExternalDataCacheDir(targetPackage, userId));
        return roots;
    }

    private void addIfExists(List<File> roots, File file) {
        if (file != null && file.exists()) {
            roots.add(file);
        }
    }

    private List<File> collectCandidateFiles(List<File> roots) {
        List<File> files = new ArrayList<>();
        Queue<FileDepth> queue = new ArrayDeque<>();
        for (File root : roots) {
            queue.add(new FileDepth(root, 0));
        }
        while (!queue.isEmpty() && files.size() < MAX_FILES) {
            FileDepth item = queue.poll();
            File file = item.file;
            if (file == null || !file.exists()) {
                continue;
            }
            if (file.isDirectory()) {
                if (item.depth >= MAX_DEPTH || shouldSkipDirectory(file)) {
                    continue;
                }
                File[] children = file.listFiles();
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    queue.add(new FileDepth(child, item.depth + 1));
                }
                continue;
            }
            if (isCandidateFile(file)) {
                files.add(file);
            }
        }
        return files;
    }

    private boolean shouldSkipDirectory(File directory) {
        String name = directory.getName();
        return "lib".equals(name)
                || "code_cache".equals(name)
                || "WebView".equalsIgnoreCase(name)
                || "app_webview".equalsIgnoreCase(name)
                || "app_flutter".equalsIgnoreCase(name)
                || "app_res_preset".equalsIgnoreCase(name)
                || "app_u4sdk".equalsIgnoreCase(name)
                || "app_plugins_lib".equalsIgnoreCase(name);
    }

    private boolean isCandidateFile(File file) {
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_FILE_BYTES) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".xml")
                || name.endsWith(".json")
                || name.endsWith(".txt")
                || name.endsWith(".dat")
                || name.endsWith(".kv")
                || name.endsWith(".conf")
                || !name.contains(".");
    }

    private ShopInfo extractFromFile(File file) {
        String content = readSmallFile(file);
        if (content == null || content.isEmpty()) {
            return null;
        }
        String id = normalizeId(firstValue(content, idKeyPattern));
        String name = normalizeName(firstValue(content, nameKeyPattern));
        if (id == null || name == null) {
            Pair urlPair = extractUrlPair(content);
            if (id == null) {
                id = normalizeId(urlPair.id);
            }
            if (name == null) {
                name = normalizeName(urlPair.name);
            }
        }
        if (id == null || name == null) {
            Pair xmlPair = extractXmlPair(content);
            if (id == null) {
                id = normalizeId(xmlPair.id);
            }
            if (name == null) {
                name = normalizeName(xmlPair.name);
            }
        }
        if (id == null || name == null) {
            Pair nearbyPair = extractNearbyPair(content);
            if (id == null) {
                id = normalizeId(nearbyPair.id);
            }
            if (name == null) {
                name = normalizeName(nearbyPair.name);
            }
        }
        if (id == null || name == null) {
            return null;
        }
        return new ShopInfo(id, name, platform);
    }

    private String firstValue(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String value = normalizeRaw(matcher.group(2));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Pair extractUrlPair(String content) {
        Pair pair = new Pair();
        pair.id = firstDecodedValue(content, idUrlPattern);
        pair.name = firstDecodedValue(content, nameUrlPattern);
        return pair;
    }

    private String firstDecodedValue(String content, Pattern pattern) {
        String value = firstValue(content, pattern);
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private Pair extractXmlPair(String content) {
        Pair pair = new Pair();
        Matcher idMatcher = Pattern.compile(String.format(XML_NAME_VALUE_PATTERN.pattern(), idKeyAlternation())).matcher(content);
        while (idMatcher.find()) {
            pair.id = firstNonBlank(idMatcher.group(2), idMatcher.group(3));
            if (pair.id != null) {
                break;
            }
        }
        if (pair.id == null) {
            Matcher selfClosingIdMatcher = Pattern.compile(String.format(XML_SELF_CLOSING_NAME_VALUE_PATTERN.pattern(), idKeyAlternation())).matcher(content);
            while (selfClosingIdMatcher.find()) {
                pair.id = normalizeRaw(selfClosingIdMatcher.group(2));
                if (pair.id != null) {
                    break;
                }
            }
        }
        Matcher nameMatcher = Pattern.compile(String.format(XML_NAME_VALUE_PATTERN.pattern(), nameKeyAlternation())).matcher(content);
        while (nameMatcher.find()) {
            pair.name = firstNonBlank(nameMatcher.group(2), nameMatcher.group(3));
            if (pair.name != null) {
                break;
            }
        }
        if (pair.name == null) {
            Matcher selfClosingNameMatcher = Pattern.compile(String.format(XML_SELF_CLOSING_NAME_VALUE_PATTERN.pattern(), nameKeyAlternation())).matcher(content);
            while (selfClosingNameMatcher.find()) {
                pair.name = normalizeRaw(selfClosingNameMatcher.group(2));
                if (pair.name != null) {
                    break;
                }
            }
        }
        return pair;
    }

    private Pair extractNearbyPair(String content) {
        Pair pair = new Pair();
        Matcher idMatcher = idKeyOnlyPattern.matcher(content);
        while (idMatcher.find()) {
            int start = Math.max(0, idMatcher.start() - 1024);
            int end = Math.min(content.length(), idMatcher.end() + 1024);
            String window = content.substring(start, end);
            String id = firstNearbyId(window, idMatcher.group(1));
            if (id == null) {
                continue;
            }
            String name = firstNearbyName(window);
            if (name != null) {
                pair.id = id;
                pair.name = name;
                return pair;
            }
        }
        return pair;
    }

    private String firstNearbyId(String content, String key) {
        if (key == null) {
            return null;
        }
        String quotedKey = Pattern.quote(key);
        Pattern directPattern = Pattern.compile("(?is)" + quotedKey + ".{0,80}?(\\d{5,20})");
        Matcher directMatcher = directPattern.matcher(content);
        while (directMatcher.find()) {
            String value = normalizeId(directMatcher.group(1));
            if (value != null) {
                return value;
            }
        }
        return normalizeId(firstValue(content, idKeyPattern));
    }

    private String firstNearbyName(String content) {
        String value = normalizeName(firstValue(content, nameKeyPattern));
        if (value != null) {
            return value;
        }
        Matcher keyMatcher = nameKeyOnlyPattern.matcher(content);
        while (keyMatcher.find()) {
            int end = Math.min(content.length(), keyMatcher.end() + 160);
            String tail = content.substring(keyMatcher.end(), end);
            Matcher valueMatcher = Pattern.compile("[\\p{L}\\p{N}（）()_\\-·]{2,80}").matcher(tail);
            while (valueMatcher.find()) {
                String candidate = normalizeName(valueMatcher.group());
                if (candidate != null && !nameKeyOnlyPattern.matcher(candidate).find()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String readSmallFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = fis.read(buffer, offset, buffer.length - offset);
                if (read <= 0) {
                    break;
                }
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Slog.w(tag, "Failed to read " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeRaw(first);
        if (normalizedFirst != null) {
            return normalizedFirst;
        }
        return normalizeRaw(second);
    }

    private String normalizeRaw(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim()
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\u0026", "&");
        return value.isEmpty() ? null : value;
    }

    private String normalizeId(String raw) {
        String value = normalizeRaw(raw);
        if (value == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\d{5,20}").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        String id = matcher.group();
        if (value.startsWith("NEW-") || value.startsWith("phase13-")) {
            return null;
        }
        return id;
    }

    private String normalizeName(String raw) {
        String value = normalizeRaw(raw);
        if (value == null) {
            return null;
        }
        if (value.startsWith("NEW-")
                || value.startsWith("新增店铺-[")
                || value.startsWith("User[")
                || value.startsWith("phase13-")
                || value.startsWith("未知")
                || value.length() > 128) {
            return null;
        }
        return value;
    }

    abstract String idKeyAlternation();

    abstract String nameKeyAlternation();

    private static class Pair {
        String id;
        String name;
    }

    private static class FileDepth {
        final File file;
        final int depth;

        FileDepth(File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }
}
