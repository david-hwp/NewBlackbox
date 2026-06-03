package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Singleton orchestrator for async shop ID extraction.
 *
 * <p>Manages a background thread for extraction operations and enforces a
 * per-package throttle to avoid excessive extraction attempts. Extraction
 * results are persisted via {@link BPackageManagerService#updateShopInfo}.</p>
 *
 * <p>Callers (e.g., {@link BActivityThread}) simply invoke
 * {@link #triggerExtract(String, int, Context)}; all async and throttle
 * logic is handled internally.</p>
 */
public class ShopIdManager {

    private static final String TAG = "ShopIdManager";
    private static final ShopIdManager sInstance = new ShopIdManager();

    private static final long EXTRACT_THROTTLE_MS = 30000L; // 30 seconds

    private final Handler mBgHandler;
    private final Map<String, Long> mLastExtractTime = new HashMap<>();

    private ShopIdManager() {
        HandlerThread handlerThread = new HandlerThread("ShopIdExtractor", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        mBgHandler = new Handler(handlerThread.getLooper());
    }

    public static ShopIdManager get() {
        return sInstance;
    }

    /**
     * Triggers async shop ID extraction for the given package and user.
     *
     * <p>If no extractor is registered for the package, or if the throttle
     * period has not elapsed since the last extraction, this method returns
     * immediately without posting work.</p>
     *
     * @param packageName the virtual app package name
     * @param userId      the virtual user ID
     * @param context     the host application context
     */
    public void triggerExtract(final String packageName, final int userId, final Context context) {
        if (!ShopIdExtractorRegistry.hasExtractor(packageName)) {
            return;
        }

        final String key = packageName + "#" + userId;
        final long lastTime = mLastExtractTime.getOrDefault(key, 0L);
        if (System.currentTimeMillis() - lastTime < EXTRACT_THROTTLE_MS) {
            Slog.d(TAG, "Throttled extraction for " + packageName + " (user " + userId + ")");
            return;
        }

        mBgHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    ShopIdExtractor extractor = ShopIdExtractorRegistry.getExtractor(packageName);
                    if (extractor == null) {
                        return;
                    }
                    ShopInfo result = extractor.extract(context, userId);
                    if (result != null) {
                        BPackageManagerService.get().updateShopInfo(packageName, userId, result);
                    }
                    mLastExtractTime.put(key, System.currentTimeMillis());
                } catch (Exception e) {
                    Slog.w(TAG, "Extraction failed for " + packageName, e);
                }
            }
        });
    }

    /**
     * Reads the currently stored shop info for a package and user.
     *
     * @param packageName the virtual app package name
     * @param userId      the virtual user ID
     * @return the stored {@link ShopInfo}, or {@code null} if none
     */
    public ShopInfo getShopInfo(String packageName, int userId) {
        return BPackageManagerService.get().getShopInfo(packageName, userId);
    }
}
