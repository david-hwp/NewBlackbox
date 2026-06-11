package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

public class MeituanWaimaiShopIdExtractor extends PatternFileShopIdExtractor {

    private static final String TARGET_PACKAGE = "com.sankuai.meituan.meituanwaimaibusiness";
    private static final String ID_KEYS = "wm_poi_id|wmPoiId|poi_id|poiId|poiID|poi_id_str|wmPoiIdStr|shopId|shop_id";
    private static final String NAME_KEYS = "poi_name|poiName|poi_name_str|wmPoiName|shopName|shop_name|storeName|store_name";
    private static final String TAG = "MeituanWaimaiShopIdExtractor";
    private static final int MAX_TARGET_FILE_BYTES = 384 * 1024;
    private static final String[] PRIORITY_CIPS_FILES = new String[]{
            "files/cips/common/com.sankuai.meituan.meituanwaimaibusiness.modules.main.request.model.PoiInfo/kv",
            "files/cips/common/com.sankuai.meituan.meituanwaimaibusiness.db.green.Poi/kv",
            "files/cips/common/com.sankuai.meituan.retail.poi.RetailPoiInfo/kv"
    };

    public MeituanWaimaiShopIdExtractor() {
        super(TARGET_PACKAGE, "meituan", TAG, ID_KEYS, NAME_KEYS);
    }

    @Override
    public ShopInfo extract(Context context, int userId) {
        File dataDir = BEnvironment.getDataDir(TARGET_PACKAGE, userId);
        for (String relativePath : PRIORITY_CIPS_FILES) {
            ShopInfo shopInfo = extractFromCipsFile(new File(dataDir, relativePath), relativePath);
            if (shopInfo != null) {
                return shopInfo;
            }
        }
        return super.extract(context, userId);
    }

    private ShopInfo extractFromCipsFile(File file, String relativePath) {
        String content = readSmallTextFile(file);
        if (content == null || content.isEmpty()) {
            return null;
        }
        boolean allowGenericId = relativePath.contains(".db.green.Poi")
                || relativePath.contains("RetailPoiInfo");
        for (String objectText : extractJsonObjects(content)) {
            ShopInfo shopInfo = parsePoiObject(objectText, allowGenericId);
            if (shopInfo != null) {
                Slog.d(TAG, "Extracted verified shop identity from CIPS " + relativePath);
                return shopInfo;
            }
        }
        return null;
    }

    private ShopInfo parsePoiObject(String objectText, boolean allowGenericId) {
        try {
            JSONObject json = new JSONObject(objectText);
            String shopId = firstNonBlank(
                    json.optString("wmPoiId", null),
                    json.optString("wmPoiIdStr", null),
                    json.optString("poiId", null),
                    json.optString("poiID", null),
                    json.optString("poi_id", null),
                    json.optString("shopId", null),
                    json.optString("shop_id", null)
            );
            if (shopId == null && allowGenericId && json.has("id")) {
                shopId = json.optString("id", null);
            }
            String shopName = firstNonBlank(
                    json.optString("poiName", null),
                    json.optString("poi_name", null),
                    json.optString("wmPoiName", null),
                    json.optString("shopName", null),
                    json.optString("shop_name", null),
                    json.optString("storeName", null),
                    json.optString("store_name", null)
            );
            if (!isActivePoi(json) || !isVerifiedShopId(shopId) || !isVerifiedShopName(shopName)) {
                return null;
            }
            return new ShopInfo(shopId.trim(), shopName.trim(), "meituan");
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isActivePoi(JSONObject json) {
        if (json.has("valid") && json.optInt("valid", 1) <= 0) {
            return false;
        }
        return !json.has("status") || json.optInt("status", 1) > 0;
    }

    private List<String> extractJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char c = content.charAt(index);
            if (start < 0) {
                if (c == '{') {
                    start = index;
                    depth = 1;
                    inString = false;
                    escaped = false;
                }
                continue;
            }

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    objects.add(content.substring(start, index + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private String readSmallTextFile(File file) {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_TARGET_FILE_BYTES) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = input.read(buffer, offset, buffer.length - offset);
                if (read <= 0) {
                    break;
                }
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to read CIPS shop file " + file.getAbsolutePath(), e);
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
            if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
                continue;
            }
            return normalized;
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
