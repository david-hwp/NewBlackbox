package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

public class EleNaposShopIdExtractor extends PatternFileShopIdExtractor {

    private static final String TARGET_PACKAGE = "me.ele.napos";
    private static final String ID_KEYS = "shopId|shop_id|storeId|store_id|restaurantId|restaurant_id|restId|rest_id|sellerId|seller_id";
    private static final String NAME_KEYS = "shopName|shop_name|storeName|store_name|restaurantName|restaurant_name|restName|rest_name|sellerName|seller_name";
    private static final String TAG = "EleNaposShopIdExtractor";
    private static final Pattern XML_STRING_PATTERN = Pattern.compile(
            "(?is)<string\\s+name=\"%s\"\\s*>(.*?)</string>");
    private static final Pattern XML_STRING_ENTRY_PATTERN = Pattern.compile(
            "(?is)<string\\s+name=\"([^\"]+)\"\\s*>(.*?)</string>");
    private static final Pattern REST_PREF_NAME_PATTERN = Pattern.compile(
            "^user_\\d+_rest_(\\d{5,20})_sp_config\\.xml$");
    private static final String[] SHOP_ID_KEYS = {
            "shopId", "shop_id",
            "storeId", "store_id",
            "restaurantId", "restaurant_id",
            "restId", "rest_id",
            "sellerId", "seller_id",
            "id", "oid"
    };
    private static final String[] TRUSTED_SHOP_NAME_KEYS = {
            "shopName", "shop_name",
            "storeName", "store_name",
            "restaurantName", "restaurant_name",
            "restName", "rest_name",
            "sellerName", "seller_name",
            "name", "title"
    };
    private static final String[] SCOPED_SHOP_NAME_KEYS = {
            "shopName", "shop_name",
            "storeName", "store_name",
            "restaurantName", "restaurant_name",
            "restName", "rest_name",
            "sellerName", "seller_name"
    };

    public EleNaposShopIdExtractor() {
        super(TARGET_PACKAGE, "ele", TAG, ID_KEYS, NAME_KEYS);
    }

    @Override
    public ShopInfo extract(Context context, int userId) {
        File sharedPrefsDir = new File(BEnvironment.getDataDir(TARGET_PACKAGE, userId), "shared_prefs");
        return extractFromSharedPrefsDir(sharedPrefsDir);
    }

    ShopInfo extractFromSharedPrefsDir(File sharedPrefsDir) {
        if (sharedPrefsDir == null) {
            return null;
        }
        String currentShopId = currentShopIdFromTracker(sharedPrefsDir);

        ShopInfo appConfig = extractFromAppConfig(sharedPrefsDir, currentShopId);
        if (appConfig != null) {
            return appConfig;
        }

        ShopInfo restScoped = extractFromRestPrefs(sharedPrefsDir, currentShopId);
        if (restScoped != null) {
            return restScoped;
        }

        if (currentShopId == null) {
            appConfig = extractFromAppConfig(sharedPrefsDir, null);
            if (appConfig != null) {
                return appConfig;
            }
            return extractFromRestPrefs(sharedPrefsDir, null);
        }
        return null;
    }

    private String currentShopIdFromTracker(File sharedPrefsDir) {
        File tracker = new File(sharedPrefsDir, "NAPOS_LTRACKER_SP.xml");
        return normalizeShopId(xmlValue(readText(tracker), "shopId"));
    }

    private ShopInfo extractFromAppConfig(File sharedPrefsDir, String preferredShopId) {
        File appConfig = new File(sharedPrefsDir, "app_sp_config.xml");
        return extractFromJsonXmlStrings(readText(appConfig), preferredShopId, appConfig.getName());
    }

    private ShopInfo extractFromRestPrefs(File sharedPrefsDir, String preferredShopId) {
        File[] files = sharedPrefsDir.listFiles((dir, name) ->
                name != null && REST_PREF_NAME_PATTERN.matcher(name).matches());
        if (files == null) {
            return null;
        }
        for (File file : files) {
            Matcher matcher = REST_PREF_NAME_PATTERN.matcher(file.getName());
            if (!matcher.matches()) {
                continue;
            }
            String shopId = normalizeShopId(matcher.group(1));
            if (shopId == null || (preferredShopId != null && !shopId.equals(preferredShopId))) {
                continue;
            }
            ShopInfo fromJson = extractFromJsonXmlStrings(readText(file), shopId, file.getName());
            if (fromJson != null) {
                return fromJson;
            }
            ShopInfo scopedName = verified(shopId, explicitScopedShopName(readText(file)), file.getName());
            if (scopedName != null) {
                return scopedName;
            }
        }
        return null;
    }

    private ShopInfo extractFromJsonXmlStrings(String content, String preferredShopId, String source) {
        if (content == null) {
            return null;
        }
        Matcher matcher = XML_STRING_ENTRY_PATTERN.matcher(content);
        while (matcher.find()) {
            String key = matcher.group(1);
            String raw = normalizeXml(matcher.group(2));
            ShopIdentity identity = findShopIdentity(parseJson(raw), preferredShopId);
            if (identity != null) {
                return verified(identity.shopId, identity.shopName, source + ":" + key);
            }
        }
        ShopIdentity identity = findShopIdentity(parseJson(content), preferredShopId);
        return identity == null ? null : verified(identity.shopId, identity.shopName, source);
    }

    private Object parseJson(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.startsWith("{")) {
                return new JSONObject(value);
            }
            if (value.startsWith("[")) {
                return new JSONArray(value);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ShopIdentity findShopIdentity(Object value, String preferredShopId) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            ShopIdentity self = identityFromObject(object, preferredShopId);
            if (self != null) {
                return self;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                ShopIdentity nested = findShopIdentity(object.opt(keys.next()), preferredShopId);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                ShopIdentity nested = findShopIdentity(array.opt(i), preferredShopId);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private ShopIdentity identityFromObject(JSONObject object, String preferredShopId) {
        String shopId = normalizeShopId(firstObjectValue(object, SHOP_ID_KEYS));
        if (shopId == null || (preferredShopId != null && !shopId.equals(preferredShopId))) {
            return null;
        }
        String shopName = firstObjectValue(object, TRUSTED_SHOP_NAME_KEYS);
        if (!isVerifiedShopName(shopName)) {
            return null;
        }
        return new ShopIdentity(shopId, shopName.trim());
    }

    private String firstObjectValue(JSONObject object, String[] keys) {
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) {
                continue;
            }
            String value = normalizeXml(String.valueOf(object.opt(key)));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String explicitScopedShopName(String content) {
        if (content == null) {
            return null;
        }
        for (String key : SCOPED_SHOP_NAME_KEYS) {
            String value = xmlValue(content, key);
            if (isVerifiedShopName(value)) {
                return value;
            }
        }
        return null;
    }

    private ShopInfo verified(String shopId, String shopName, String source) {
        String normalizedShopId = normalizeShopId(shopId);
        if (!isVerifiedShopId(normalizedShopId) || !isVerifiedShopName(shopName)) {
            return null;
        }
        String normalizedShopName = shopName.trim();
        logDebug("Extracted shopId=" + normalizedShopId + ", shopName=" + normalizedShopName + " from " + source);
        return new ShopInfo(normalizedShopId, normalizedShopName, "ele");
    }

    private String xmlValue(String content, String key) {
        if (content == null || key == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(String.format(XML_STRING_PATTERN.pattern(), Pattern.quote(key))).matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return normalizeXml(matcher.group(1));
    }

    private String normalizeXml(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'")
                .replace("\\/", "/");
        return normalized.isEmpty() ? null : normalized;
    }

    private String readText(File file) {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > 256 * 1024) {
            return null;
        }
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read <= 0) {
                        break;
                    }
                    offset += read;
                }
                return new String(bytes, 0, offset, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            try {
                Slog.w(TAG, "Failed to read " + file.getAbsolutePath(), e);
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    private String normalizeShopId(String shopId) {
        if (shopId == null) {
            return null;
        }
        String value = shopId.trim();
        if (value.startsWith("NEW-") || value.startsWith("phase13-")) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\d{5,20}").matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean isVerifiedShopId(String shopId) {
        return shopId != null && shopId.matches("\\d{5,20}");
    }

    private boolean isVerifiedShopName(String shopName) {
        if (shopName == null) {
            return false;
        }
        String value = shopName.trim();
        return !value.isEmpty()
                && !value.startsWith("NEW-")
                && !value.startsWith("新增店铺-[")
                && !value.startsWith("User[")
                && !value.startsWith("phase13-")
                && !value.startsWith("未知")
                && value.length() <= 128;
    }

    private void logDebug(String message) {
        try {
            Slog.d(TAG, message);
        } catch (Throwable ignored) {
        }
    }

    @Override
    String idKeyAlternation() {
        return ID_KEYS;
    }

    @Override
    String nameKeyAlternation() {
        return NAME_KEYS;
    }

    private static class ShopIdentity {
        final String shopId;
        final String shopName;

        ShopIdentity(String shopId, String shopName) {
            this.shopId = shopId;
            this.shopName = shopName;
        }
    }
}
