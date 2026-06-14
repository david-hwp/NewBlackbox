package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

abstract class JsonSnippetShopIdExtractor implements ShopIdExtractor {

    private static final int MAX_FILE_BYTES = 512 * 1024;
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[^{}]{0,24000}\\}");
    private static final int NEARBY_WINDOW_CHARS = 256;

    private final String targetPackage;
    private final String platform;
    private final String tag;
    private final String[] relativeFiles;
    private final String[] idKeys;
    private final String[] nameKeys;

    JsonSnippetShopIdExtractor(
            String targetPackage,
            String platform,
            String tag,
            String[] relativeFiles,
            String[] idKeys,
            String[] nameKeys
    ) {
        this.targetPackage = targetPackage;
        this.platform = platform;
        this.tag = tag;
        this.relativeFiles = relativeFiles;
        this.idKeys = idKeys;
        this.nameKeys = nameKeys;
    }

    @Override
    public String getTargetPackage() {
        return targetPackage;
    }

    @Override
    public ShopInfo extract(Context context, int userId) {
        File dataDir = BEnvironment.getDataDir(targetPackage, userId);
        for (String relativeFile : relativeFiles) {
            ShopInfo info = extractFromFile(new File(dataDir, relativeFile), relativeFile);
            if (info != null) {
                return info;
            }
        }
        return null;
    }

    ShopInfo extractFromFile(File file, String source) {
        String content = readSmallTextFile(file);
        if (content == null || content.isEmpty()) {
            return null;
        }
        String normalizedContent = unescape(content);
        for (String candidate : jsonCandidates(normalizedContent)) {
            ShopInfo info = extractFromJson(candidate, source);
            if (info != null) {
                return info;
            }
        }
        String id = normalizeId(regexValue(normalizedContent, idKeys));
        String name = normalizeName(regexValue(normalizedContent, nameKeys));
        if (id == null || name == null) {
            Pair nearbyPair = nearbyValuePair(normalizedContent);
            if (id == null) {
                id = normalizeId(nearbyPair.id);
            }
            if (name == null) {
                name = normalizeName(nearbyPair.name);
            }
        }
        if (id != null && name != null) {
            logExtracted(source);
            return new ShopInfo(id, name, platform);
        }
        return null;
    }

    private ShopInfo extractFromJson(String candidate, String source) {
        try {
            JSONObject json = new JSONObject(candidate);
            String id = normalizeId(firstJsonValue(json, idKeys));
            String name = normalizeName(firstJsonValue(json, nameKeys));
            if (id == null || name == null) {
                return null;
            }
            logExtracted(source);
            return new ShopInfo(id, name, platform);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void logExtracted(String source) {
        try {
            Slog.d(tag, "Extracted verified shop identity from " + source);
        } catch (RuntimeException ignored) {
            // Local JVM tests use Android stubs where Log.println throws.
        }
    }

    private List<String> jsonCandidates(String content) {
        List<String> values = new ArrayList<>();
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(content);
        while (matcher.find() && values.size() < 80) {
            values.add(unescape(matcher.group()));
        }
        return values;
    }

    private String firstJsonValue(JSONObject json, String[] keys) {
        for (String key : keys) {
            if (json.has(key) && !json.isNull(key)) {
                String value = normalizeText(String.valueOf(json.opt(key)));
                if (value != null) {
                    return value;
                }
            }
        }
        for (String key : keys) {
            String nested = findKeyValue(json, key, 0);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private String findKeyValue(Object value, String key, int depth) {
        if (value == null || depth > 8) {
            return null;
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has(key) && !object.isNull(key)) {
                return normalizeText(String.valueOf(object.opt(key)));
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String found = findKeyValue(object.opt(keys.next()), key, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } else if (value instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String found = findKeyValue(array.opt(i), key, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String regexValue(String content, String[] keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile(
                    "(?is)(?:\\\\?[\\\"'])?" + Pattern.quote(key) + "(?:\\\\?[\\\"'])?\\s*[:=]\\s*(?:\\\\?[\\\"'])?([^\\\\\\\"'{}\\[\\],\\r\\n]{1,160})"
            ).matcher(content);
            if (matcher.find()) {
                String value = normalizeText(matcher.group(1));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private Pair nearbyValuePair(String content) {
        Pair pair = new Pair();
        for (String key : idKeys) {
            Matcher matcher = Pattern.compile("(?is)" + Pattern.quote(key)).matcher(content);
            while (matcher.find()) {
                int start = Math.max(0, matcher.start() - NEARBY_WINDOW_CHARS);
                int end = Math.min(content.length(), matcher.end() + NEARBY_WINDOW_CHARS);
                String window = content.substring(start, end);
                String id = normalizeId(regexValue(window, idKeys));
                if (id == null) {
                    id = normalizeId(valueAfterKey(window, key, true));
                }
                if (id == null) {
                    continue;
                }
                String name = normalizeName(regexValue(window, nameKeys));
                if (name == null) {
                    name = normalizeName(firstValueAfterAnyKey(window, nameKeys, false));
                }
                if (name != null) {
                    pair.id = id;
                    pair.name = name;
                    return pair;
                }
            }
        }
        return pair;
    }

    private String firstValueAfterAnyKey(String content, String[] keys, boolean numericOnly) {
        for (String key : keys) {
            String value = valueAfterKey(content, key, numericOnly);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String valueAfterKey(String content, String key, boolean numericOnly) {
        if (content == null || key == null) {
            return null;
        }
        Matcher keyMatcher = Pattern.compile("(?is)" + Pattern.quote(key)).matcher(content);
        while (keyMatcher.find()) {
            int end = Math.min(content.length(), keyMatcher.end() + 180);
            String tail = content.substring(keyMatcher.end(), end);
            Matcher valueMatcher = Pattern.compile(numericOnly
                    ? "\\d{5,20}"
                    : "[\\p{L}\\p{N}（）()_\\-·]{2,80}").matcher(tail);
            while (valueMatcher.find()) {
                String candidate = normalizeText(valueMatcher.group());
                if (candidate == null || containsConfiguredKey(candidate)) {
                    continue;
                }
                if (!numericOnly && candidate.endsWith("s")
                        && valueMatcher.end() < tail.length()
                        && Character.isISOControl(tail.charAt(valueMatcher.end()))) {
                    candidate = normalizeText(candidate.substring(0, candidate.length() - 1));
                }
                return candidate;
            }
        }
        return null;
    }

    private boolean containsConfiguredKey(String value) {
        for (String key : idKeys) {
            if (value.contains(key)) {
                return true;
            }
        }
        for (String key : nameKeys) {
            if (value.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String readSmallTextFile(File file) {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_FILE_BYTES) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read <= 0) {
                    break;
                }
                offset += read;
            }
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Slog.w(tag, "Failed to read shop identity file " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private String normalizeId(String raw) {
        String value = normalizeText(raw);
        if (value == null || value.startsWith("NEW-") || value.startsWith("phase13-")) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\d{5,20}").matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private String normalizeName(String raw) {
        String value = normalizeText(raw);
        if (value == null
                || value.startsWith("NEW-")
                || value.startsWith("新增店铺-[")
                || value.startsWith("User[")
                || value.startsWith("phase13-")
                || value.startsWith("未知")
                || value.length() > 128) {
            return null;
        }
        return value;
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String value = unescape(raw).trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private String unescape(String raw) {
        return raw
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\u003d", "=")
                .replace("\\u0026", "&");
    }

    private static class Pair {
        String id;
        String name;
    }
}
