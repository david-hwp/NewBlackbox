package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

/**
 * JD-specific shop ID extractor for the Jingming (京明管家) app.
 *
 * <p>Strategy order:
 * <ol>
 *   <li><b>SharedPreferences (primary)</b> — reads {@code storeId} and
 *       {@code storeName} directly from {@code JingmingAndroidClient.xml}.
 *       This is the most reliable approach because the values are stored as
 *       plain strings after login.</li>
 *   <li><b>GeTui alias fallback</b> — reads {@code ge_tui_push_alias_bind_flag}
 *       from the same file (value format: {@code shopId,true}). Kept for
 *       backward compatibility with older app versions.</li>
 * </ol>
 *
 * <p>All operations are read-only and wrapped in try-catch. The extractor
 * never modifies app data.
 */
public class JDShopIdExtractor implements ShopIdExtractor {

    private static final String TAG = "JDShopIdExtractor";
    private static final String TARGET_PACKAGE = "com.jd.mrd.jingming";
    private static final String PREFS_FILE = "JingmingAndroidClient";

    // Matches: <string name="storeId">14395758</string>
    private static final Pattern STORE_ID_PATTERN =
            Pattern.compile("<string name=\"storeId\">([^<]+)</string>");

    // Matches: <string name="storeName">罗家臭豆腐(东瓜山店)</string>
    private static final Pattern STORE_NAME_PATTERN =
            Pattern.compile("<string name=\"storeName\">([^<]+)</string>");

    // Matches: <string name="ge_tui_push_alias_bind_flag">14395758,true</string>
    private static final Pattern GETUI_PATTERN =
            Pattern.compile("<string name=\"ge_tui_push_alias_bind_flag\">([^<]+)</string>");

    @Override
    public String getTargetPackage() {
        return TARGET_PACKAGE;
    }

    @Override
    public ShopInfo extract(Context context, int userId) {
        File prefsFile = BEnvironment.getXSharedPreferences(TARGET_PACKAGE, PREFS_FILE);
        if (prefsFile == null || !prefsFile.exists()) {
            Slog.d(TAG, "SharedPreferences file not found: " + prefsFile);
            return null;
        }

        String content = readFileToString(prefsFile);
        if (content == null || content.isEmpty()) {
            Slog.w(TAG, "SharedPreferences file is empty");
            return null;
        }

        // Strategy 1: extract storeId + storeName directly (primary)
        String shopId = extractPattern(content, STORE_ID_PATTERN);
        String shopName = extractPattern(content, STORE_NAME_PATTERN);
        if (shopId != null && isValidShopId(shopId)) {
            Slog.d(TAG, "Extracted shopId=" + shopId + ", shopName=" + shopName + " from SharedPreferences");
            return new ShopInfo(shopId, shopName, "jd");
        }

        // Strategy 2: fallback to ge_tui_push_alias_bind_flag
        String getuiValue = extractPattern(content, GETUI_PATTERN);
        if (getuiValue != null && !getuiValue.isEmpty()) {
            String fallbackShopId = getuiValue.split(",")[0].trim();
            if (isValidShopId(fallbackShopId)) {
                Slog.d(TAG, "Extracted shopId=" + fallbackShopId + " from GeTui alias");
                return new ShopInfo(fallbackShopId, null, "jd");
            }
        }

        Slog.d(TAG, "No shopId found in " + PREFS_FILE + ".xml");
        return null;
    }

    /**
     * Extracts the first capture group from {@code content} matching {@code pattern},
     * or {@code null} if no match.
     */
    private String extractPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Validates that a shop ID is non-empty and numeric.
     *
     * <p>Per T-04-03 (tampering mitigation): reject malformed or non-numeric IDs.
     */
    private boolean isValidShopId(String shopId) {
        if (shopId == null || shopId.isEmpty()) {
            return false;
        }
        // Must be purely numeric
        for (int i = 0; i < shopId.length(); i++) {
            char c = shopId.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a small text file into a String. Returns null on any error.
     */
    private String readFileToString(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
             java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, StandardCharsets.UTF_8);
             java.io.BufferedReader reader = new java.io.BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            Slog.w(TAG, "Failed to read file: " + file, e);
            return null;
        }
    }
}
