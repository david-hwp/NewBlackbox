package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

public class EleNaposShopIdExtractor extends PatternFileShopIdExtractor {

    private static final String TARGET_PACKAGE = "me.ele.napos";
    private static final String ID_KEYS = "shopId|shop_id|storeId|store_id|restaurantId|restaurant_id|restId|rest_id|sellerId|seller_id";
    private static final String NAME_KEYS = "shopName|shop_name|storeName|store_name|restaurantName|restaurant_name|restName|rest_name|sellerName|seller_name|user_name|username";
    private static final String TAG = "EleNaposShopIdExtractor";
    private static final Pattern XML_STRING_PATTERN = Pattern.compile(
            "(?is)<string\\s+name=\"%s\"\\s*>(.*?)</string>");
    private static final Pattern REST_PREF_NAME_PATTERN = Pattern.compile(
            "^user_\\d+_rest_(\\d{5,20})_sp_config\\.xml$");

    public EleNaposShopIdExtractor() {
        super(TARGET_PACKAGE, "ele", TAG, ID_KEYS, NAME_KEYS);
    }

    @Override
    public ShopInfo extract(Context context, int userId) {
        File sharedPrefsDir = new File(BEnvironment.getDataDir(TARGET_PACKAGE, userId), "shared_prefs");
        ShopInfo primary = extractFromTracker(sharedPrefsDir);
        if (primary != null) {
            return primary;
        }
        ShopInfo switchUserInfo = extractFromSwitchLoginUserInfo(sharedPrefsDir);
        if (switchUserInfo != null) {
            return switchUserInfo;
        }
        ShopInfo fallback = super.extract(context, userId);
        if (fallback != null) {
            return fallback;
        }
        return extractFromRestPrefFileName(sharedPrefsDir);
    }

    private ShopInfo extractFromTracker(File sharedPrefsDir) {
        File tracker = new File(sharedPrefsDir, "NAPOS_LTRACKER_SP.xml");
        String content = readText(tracker);
        if (content == null) {
            return null;
        }
        String shopId = xmlValue(content, "shopId");
        String shopName = firstNonBlank(
                xmlValue(content, "shopName"),
                xmlValue(content, "user_name"),
                xmlValue(content, "username")
        );
        return verified(shopId, shopName, tracker.getName());
    }

    private ShopInfo extractFromSwitchLoginUserInfo(File sharedPrefsDir) {
        File appConfig = new File(sharedPrefsDir, "app_sp_config.xml");
        String content = readText(appConfig);
        if (content == null) {
            return null;
        }
        String raw = xmlValue(content, "switch_login_user_info");
        String shopName = null;
        if (raw != null) {
            try {
                JSONArray users = new JSONArray(raw);
                for (int i = 0; i < users.length(); i++) {
                    JSONObject user = users.optJSONObject(i);
                    if (user == null) {
                        continue;
                    }
                    shopName = firstNonBlank(
                            user.optString("shopName", null),
                            user.optString("username", null)
                    );
                    if (isVerifiedShopName(shopName)) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        String shopId = firstRestPrefId(sharedPrefsDir);
        return verified(shopId, shopName, appConfig.getName());
    }

    private ShopInfo extractFromRestPrefFileName(File sharedPrefsDir) {
        String shopId = firstRestPrefId(sharedPrefsDir);
        String content = readText(new File(sharedPrefsDir, "NAPOS_LTRACKER_SP.xml"));
        String shopName = content == null ? null : xmlValue(content, "user_name");
        return verified(shopId, shopName, "rest-pref-name");
    }

    private String firstRestPrefId(File sharedPrefsDir) {
        File[] files = sharedPrefsDir.listFiles((dir, name) ->
                name != null && REST_PREF_NAME_PATTERN.matcher(name).matches());
        if (files == null) {
            return null;
        }
        for (File file : files) {
            Matcher matcher = REST_PREF_NAME_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                String shopId = matcher.group(1);
                if (isVerifiedShopId(shopId)) {
                    return shopId;
                }
            }
        }
        return null;
    }

    private ShopInfo verified(String shopId, String shopName, String source) {
        if (!isVerifiedShopId(shopId) || !isVerifiedShopName(shopName)) {
            return null;
        }
        Slog.d(TAG, "Extracted shopId=" + shopId + ", shopName=" + shopName + " from " + source);
        return new ShopInfo(shopId.trim(), shopName.trim(), "ele");
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
            Slog.w(TAG, "Failed to read " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim();
            if (!normalized.isEmpty() && !"null".equalsIgnoreCase(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private boolean isVerifiedShopId(String shopId) {
        if (shopId == null) {
            return false;
        }
        String value = shopId.trim();
        if (value.startsWith("NEW-") || value.startsWith("phase13-")) {
            return false;
        }
        return value.matches("\\d{5,20}");
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

    @Override
    String idKeyAlternation() {
        return ID_KEYS;
    }

    @Override
    String nameKeyAlternation() {
        return NAME_KEYS;
    }
}
