package top.niunaijun.blackbox.core.system.pm;

import android.content.Context;

import top.niunaijun.blackbox.entity.pm.ShopInfo;

/**
 * Generic interface for extracting shop ID information from a virtual app.
 *
 * <p>Implementations are registered in {@link ShopIdExtractorRegistry} and
 * looked up by target package name. Each extractor is responsible for one
 * platform (e.g., JD, Taobao, Meituan).</p>
 *
 * <p>Extraction must be read-only and safe to call from a background thread.
 * Implementations should never modify app data and must handle all exceptions
 * gracefully, returning {@code null} on failure.</p>
 */
public interface ShopIdExtractor {

    /**
     * Attempts to extract shop information for the given virtual app.
     *
     * @param context  the host application context
     * @param userId   the virtual user ID under which the app is installed
     * @return a {@link ShopInfo} instance if extraction succeeds, or {@code null}
     */
    ShopInfo extract(Context context, int userId);

    /**
     * Returns the package name this extractor handles.
     *
     * @return the target package name (e.g., "com.jd.mrd.jingming")
     */
    String getTargetPackage();
}
