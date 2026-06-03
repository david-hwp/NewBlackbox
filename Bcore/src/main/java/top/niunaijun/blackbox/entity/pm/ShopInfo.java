package top.niunaijun.blackbox.entity.pm;

/**
 * Data class representing extracted shop information from a virtual app.
 *
 * <p>Instances are created by {@link top.niunaijun.blackbox.core.system.pm.ShopIdExtractor}
 * implementations and consumed by the UI layer for display.</p>
 */
public class ShopInfo {
    /** The extracted numeric shop ID (e.g., "16364870"). */
    public String shopId;
    /** Optional human-readable shop name. May be null if not available. */
    public String shopName;
    /** Platform identifier: "jd", "taobao", "meituan", etc. */
    public String platform;
    /** Timestamp (System.currentTimeMillis()) when the extraction occurred. */
    public long extractedAt;

    /**
     * Constructs a ShopInfo with the given fields and sets extractedAt to now.
     */
    public ShopInfo(String shopId, String shopName, String platform) {
        this.shopId = shopId;
        this.shopName = shopName;
        this.platform = platform;
        this.extractedAt = System.currentTimeMillis();
    }

    /**
     * No-arg constructor for deserialization or framework use.
     */
    public ShopInfo() {
    }

    @Override
    public String toString() {
        return "ShopInfo{shopId='" + shopId + "', shopName='" + shopName + "', platform='" + platform + "', extractedAt=" + extractedAt + "}";
    }
}
