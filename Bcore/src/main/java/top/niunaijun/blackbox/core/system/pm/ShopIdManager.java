package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Singleton orchestrator for async shop ID extraction.
 *
 * <p>Manages a background thread for extraction operations. Extraction results
 * are persisted via {@link BPackageManagerService#updateShopInfo}.</p>
 *
 * <p>Callers (e.g., {@link BActivityThread}) simply invoke
 * {@link #triggerExtract(String, int, Context)}; async execution is handled
 * internally.</p>
 */
public class ShopIdManager {

    private static final String TAG = "ShopIdManager";
    private static final ShopIdManager sInstance = new ShopIdManager();

    private final Handler mBgHandler;

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
     * <p>If no extractor is registered for the package, this method returns
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

        mBgHandler.post(new Runnable() {
            @Override
            public void run() {
                extractNow(packageName, userId, context);
            }
        });
    }

    /**
     * Synchronously extracts shop information and persists the latest result.
     *
     * @return extracted info, or the stored previous info if current extraction returns null
     */
    public ShopInfo extractNow(String packageName, int userId, Context context) {
        ShopIdExtractor extractor = ShopIdExtractorRegistry.getExtractor(packageName);
        if (extractor == null) {
            return getShopInfo(packageName, userId);
        }
        try {
            ShopInfo result = extractor.extract(context, userId);
            if (result != null) {
                result.packageName = packageName;
                result.userId = userId;
                BPackageManager.get().updateShopInfo(packageName, userId, result);
                return result;
            }
        } catch (Exception e) {
            Slog.w(TAG, "Extraction failed for " + packageName, e);
        }
        ShopInfo stored = getShopInfo(packageName, userId);
        if (stored != null) {
            stored.packageName = packageName;
            stored.userId = userId;
        }
        return stored;
    }

    /**
     * Reads the currently stored shop info for a package and user.
     *
     * @param packageName the virtual app package name
     * @param userId      the virtual user ID
     * @return the stored {@link ShopInfo}, or {@code null} if none
     */
    public ShopInfo getShopInfo(String packageName, int userId) {
        return BPackageManager.get().getShopInfo(packageName, userId);
    }
}
