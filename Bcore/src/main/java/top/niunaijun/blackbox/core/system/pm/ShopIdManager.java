package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Singleton orchestrator for app-triggered shop identity extraction.
 *
 * <p>The engine does not scan proactively. The main app invokes the engine
 * binder when the shop list is refreshed or after a shop card is opened, and
 * this manager reads local clone data synchronously for the requested virtual
 * user.</p>
 */
public class ShopIdManager {

    private static final String TAG = "ShopIdManager";
    private static final ShopIdManager sInstance = new ShopIdManager();

    private ShopIdManager() {
    }

    public static ShopIdManager get() {
        return sInstance;
    }

    /**
     * Synchronously extracts shop information and persists the latest result.
     *
     * @return extracted info from the current clone data, or {@code null} when
     * no verified identity can be read now
     */
    public ShopInfo extractNow(String packageName, int userId, Context context) {
        ShopIdExtractor extractor = ShopIdExtractorRegistry.getExtractor(packageName);
        if (extractor == null) {
            return null;
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
        return null;
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
