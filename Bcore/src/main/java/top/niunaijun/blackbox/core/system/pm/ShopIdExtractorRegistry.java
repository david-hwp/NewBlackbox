package top.niunaijun.blackbox.core.system.pm;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton registry that maps package names to {@link ShopIdExtractor} instances.
 *
 * <p>Extractors are registered statically at class-load time. Runtime
 * registration is also supported via {@link #register(ShopIdExtractor)}.
 * The registry is thread-safe for registration operations.</p>
 */
public class ShopIdExtractorRegistry {

    private static final Map<String, ShopIdExtractor> EXTRACTORS = new HashMap<>();

    static {
        // JD Jingming (京明管家)
        EXTRACTORS.put("com.jd.mrd.jingming", new JDShopIdExtractor());

        // Extension placeholders — uncomment and instantiate when extractors are implemented
        // EXTRACTORS.put("com.taobao.taobao", new TaobaoShopIdExtractor());
        // EXTRACTORS.put("com.sankuai.meituan", new MeituanShopIdExtractor());
    }

    private ShopIdExtractorRegistry() {
    }

    /**
     * Returns the extractor registered for the given package name, or {@code null}.
     */
    public static ShopIdExtractor getExtractor(String packageName) {
        return EXTRACTORS.get(packageName);
    }

    /**
     * Registers an extractor at runtime. Thread-safe.
     */
    public static void register(ShopIdExtractor extractor) {
        synchronized (EXTRACTORS) {
            EXTRACTORS.put(extractor.getTargetPackage(), extractor);
        }
    }

    /**
     * Returns {@code true} if an extractor exists for the given package name.
     */
    public static boolean hasExtractor(String packageName) {
        return EXTRACTORS.containsKey(packageName);
    }
}
