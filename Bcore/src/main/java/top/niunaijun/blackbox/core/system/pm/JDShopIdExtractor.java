package top.niunaijun.blackbox.core.system.pm;

import android.app.ActivityManager;
import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

/**
 * JD-specific shop ID extractor for the Jingming (京明管家) app.
 *
 * <p>Strategy order:
 * <ol>
 *   <li><b>SharedPreferences</b> — reads {@code ge_tui_push_alias_bind_flag} from
 *       {@code JingmingAndroidClient.xml} (value format: {@code shopId,true})</li>
 *   <li><b>WebView DevTools</b> — scans {@code /proc/net/unix} for the JD process's
 *       {@code webview_devtools_remote_} socket, connects via {@link LocalSocket},
 *       queries {@code /json/list}, and parses the {@code storeId} query param from URLs</li>
 * </ol>
 *
 * <p>All operations are read-only and wrapped in try-catch. A 5-second timeout
 * guards the entire extraction. The extractor never modifies app data.
 */
public class JDShopIdExtractor implements ShopIdExtractor {

    private static final String TAG = "JDShopIdExtractor";
    private static final String TARGET_PACKAGE = "com.jd.mrd.jingming";
    private static final String PREFS_FILE = "JingmingAndroidClient";
    private static final String PREFS_KEY = "ge_tui_push_alias_bind_flag";
    private static final long TIMEOUT_MS = 5000L;

    // Matches: <string name="ge_tui_push_alias_bind_flag">16364870,true</string>
    private static final Pattern PREFS_VALUE_PATTERN =
            Pattern.compile("<string name=\"" + PREFS_KEY + "\">([^<]+)</string>");

    // Matches: webview_devtools_remote_12345
    private static final Pattern DEVTOOLS_SOCKET_PATTERN =
            Pattern.compile("webview_devtools_remote_(\\d+)");

    @Override
    public String getTargetPackage() {
        return TARGET_PACKAGE;
    }

    @Override
    public ShopInfo extract(Context context, int userId) {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        // Strategy 1: SharedPreferences (most reliable)
        try {
            ShopInfo info = extractFromSharedPreferences(userId);
            if (info != null) {
                Slog.d(TAG, "Extracted shopId from SharedPreferences: " + info.shopId);
                return info;
            }
        } catch (Exception e) {
            Slog.w(TAG, "SharedPreferences extraction failed", e);
        }

        if (System.currentTimeMillis() > deadline) {
            Slog.w(TAG, "Extraction timed out after SharedPreferences attempt");
            return null;
        }

        // Strategy 2: WebView DevTools fallback
        try {
            ShopInfo info = extractFromWebViewDevTools(context, deadline);
            if (info != null) {
                Slog.d(TAG, "Extracted shopId from WebView DevTools: " + info.shopId);
                return info;
            }
        } catch (Exception e) {
            Slog.w(TAG, "WebView DevTools extraction failed", e);
        }

        Slog.d(TAG, "All extraction strategies failed for " + TARGET_PACKAGE);
        return null;
    }

    /**
     * Reads the JD app's SharedPreferences XML file directly and parses the
     * {@code ge_tui_push_alias_bind_flag} value.
     *
     * <p>Expected value format: {@code 16364870,true}
     */
    private ShopInfo extractFromSharedPreferences(int userId) {
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

        Matcher matcher = PREFS_VALUE_PATTERN.matcher(content);
        if (!matcher.find()) {
            Slog.d(TAG, "Key '" + PREFS_KEY + "' not found in SharedPreferences");
            return null;
        }

        String rawValue = matcher.group(1);
        if (rawValue == null || rawValue.isEmpty()) {
            Slog.w(TAG, "Empty value for key '" + PREFS_KEY + "'");
            return null;
        }

        // Value format: "16364870,true" — split on comma, first part is shopId
        String shopId = rawValue.split(",")[0].trim();
        if (!isValidShopId(shopId)) {
            Slog.w(TAG, "Invalid shopId format: '" + shopId + "'");
            return null;
        }

        return new ShopInfo(shopId, null, "jd");
    }

    /**
     * Attempts to extract shopId by connecting to the JD app's WebView DevTools
     * remote debugging socket and querying the open page list.
     *
     * <p>Steps:
     * <ol>
     *   <li>Find JD process PID via ActivityManager</li>
     *   <li>Scan {@code /proc/net/unix} for {@code webview_devtools_remote_{pid}}</li>
     *   <li>Connect via {@link LocalSocket} (abstract namespace)</li>
     *   <li>Send HTTP GET {@code /json/list}</li>
     *   <li>Parse JSON for {@code url} fields, extract {@code storeId} query param</li>
     * </ol>
     */
    private ShopInfo extractFromWebViewDevTools(Context context, long deadline) {
        int pid = findProcessPid(context, TARGET_PACKAGE);
        if (pid <= 0) {
            Slog.d(TAG, "JD process not running, skipping WebView DevTools");
            return null;
        }

        String socketName = findDevToolsSocket(pid);
        if (socketName == null) {
            Slog.d(TAG, "No WebView DevTools socket found for PID " + pid);
            return null;
        }

        if (System.currentTimeMillis() > deadline) {
            Slog.w(TAG, "Timeout before WebView socket connection");
            return null;
        }

        String jsonResponse = queryDevToolsJsonList(socketName, deadline);
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            Slog.d(TAG, "Empty DevTools JSON response");
            return null;
        }

        String storeId = parseStoreIdFromJson(jsonResponse);
        if (storeId != null && isValidShopId(storeId)) {
            return new ShopInfo(storeId, null, "jd");
        }

        Slog.d(TAG, "No storeId found in DevTools response");
        return null;
    }

    /**
     * Finds the PID of the target package's process via ActivityManager.
     */
    private int findProcessPid(Context context, String packageName) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return -1;
            }
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes == null) {
                return -1;
            }
            for (ActivityManager.RunningAppProcessInfo info : processes) {
                if (packageName.equals(info.processName)) {
                    return info.pid;
                }
            }
        } catch (Exception e) {
            Slog.w(TAG, "Failed to find process PID", e);
        }
        return -1;
    }

    /**
     * Scans {@code /proc/net/unix} for a WebView DevTools socket matching the given PID.
     */
    private String findDevToolsSocket(int pid) {
        String content = readProcNetUnix();
        if (content == null) {
            return null;
        }

        // Try exact PID match first
        Matcher matcher = DEVTOOLS_SOCKET_PATTERN.matcher(content);
        while (matcher.find()) {
            String foundPid = matcher.group(1);
            if (String.valueOf(pid).equals(foundPid)) {
                return "webview_devtools_remote_" + foundPid;
            }
        }

        // Fallback: accept any webview_devtools_remote socket if exact PID not found
        // (the JD app may have multiple processes)
        matcher = DEVTOOLS_SOCKET_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(0);
        }

        return null;
    }

    /**
     * Reads {@code /proc/net/unix} via Runtime.exec as a fallback when direct file
     * access may be restricted.
     */
    private String readProcNetUnix() {
        // Try direct file read first (faster, no process spawn)
        String direct = readFileToString(new File("/proc/net/unix"));
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }

        // Fallback: use cat via Runtime.exec
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("cat /proc/net/unix");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Slog.w(TAG, "Failed to read /proc/net/unix", e);
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * Connects to the DevTools abstract socket and sends an HTTP GET for {@code /json/list}.
     */
    private String queryDevToolsJsonList(String socketName, long deadline) {
        LocalSocket socket = new LocalSocket();
        try {
            LocalSocketAddress address = new LocalSocketAddress(
                    socketName, LocalSocketAddress.Namespace.ABSTRACT);
            long connectTimeout = Math.min(2000L, deadline - System.currentTimeMillis());
            if (connectTimeout <= 0) {
                return null;
            }
            socket.connect(address);

            // Build minimal HTTP GET request
            String request = "GET /json/list HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Connection: close\r\n\r\n";

            OutputStream os = socket.getOutputStream();
            os.write(request.getBytes(StandardCharsets.UTF_8));
            os.flush();

            // Read response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                boolean headersDone = false;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        headersDone = true;
                        continue;
                    }
                    if (headersDone) {
                        sb.append(line);
                    }
                    if (System.currentTimeMillis() > deadline) {
                        Slog.w(TAG, "DevTools read timed out");
                        break;
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Slog.w(TAG, "DevTools socket query failed", e);
            return null;
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Parses the DevTools {@code /json/list} JSON response for {@code storeId} in URLs.
     */
    private String parseStoreIdFromJson(String json) {
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String url = obj.optString("url", null);
                if (url == null || url.isEmpty()) {
                    continue;
                }
                String storeId = extractQueryParam(url, "storeId");
                if (storeId != null && !storeId.isEmpty()) {
                    return storeId;
                }
            }
        } catch (Exception e) {
            Slog.w(TAG, "Failed to parse DevTools JSON", e);
        }
        return null;
    }

    /**
     * Extracts a query parameter value from a URL string.
     */
    private String extractQueryParam(String url, String paramName) {
        try {
            String query = url;
            int qIdx = query.indexOf('?');
            if (qIdx >= 0) {
                query = query.substring(qIdx + 1);
            }
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int eqIdx = pair.indexOf('=');
                if (eqIdx > 0) {
                    String key = pair.substring(0, eqIdx);
                    String value = pair.substring(eqIdx + 1);
                    if (key.equals(paramName)) {
                        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                    }
                }
            }
        } catch (Exception e) {
            Slog.w(TAG, "Failed to extract query param", e);
        }
        return null;
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
