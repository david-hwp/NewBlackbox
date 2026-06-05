package top.niunaijun.blackbox.entity.pm;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Data class representing extracted shop information from a virtual app.
 *
 * <p>Instances are created by {@link top.niunaijun.blackbox.core.system.pm.ShopIdExtractor}
 * implementations and consumed by the UI layer for display.</p>
 */
public class ShopInfo implements Parcelable {
    /** The extracted numeric shop ID (e.g., "16364870"). */
    public String shopId;
    /** Optional human-readable shop name. May be null if not available. */
    public String shopName;
    /** Platform identifier: "jd", "taobao", "meituan", etc. */
    public String platform;
    /** Timestamp (System.currentTimeMillis()) when the extraction occurred. */
    public long extractedAt;
    /** Virtual app package name that produced this info. */
    public String packageName;
    /** Virtual user ID that produced this info. */
    public int userId;

    /**
     * Constructs a ShopInfo with the given fields and sets extractedAt to now.
     */
    public ShopInfo(String shopId, String shopName, String platform) {
        this.shopId = shopId;
        this.shopName = shopName;
        this.platform = platform;
        this.extractedAt = System.currentTimeMillis();
        this.userId = -1;
    }

    public ShopInfo(String shopId, String shopName, String platform, String packageName, int userId) {
        this(shopId, shopName, platform);
        this.packageName = packageName;
        this.userId = userId;
    }

    /**
     * No-arg constructor for deserialization or framework use.
     */
    public ShopInfo() {
    }

    protected ShopInfo(Parcel in) {
        this.shopId = in.readString();
        this.shopName = in.readString();
        this.platform = in.readString();
        this.extractedAt = in.readLong();
        this.packageName = in.readString();
        this.userId = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.shopId);
        dest.writeString(this.shopName);
        dest.writeString(this.platform);
        dest.writeLong(this.extractedAt);
        dest.writeString(this.packageName);
        dest.writeInt(this.userId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ShopInfo> CREATOR = new Parcelable.Creator<ShopInfo>() {
        @Override
        public ShopInfo createFromParcel(Parcel source) {
            return new ShopInfo(source);
        }

        @Override
        public ShopInfo[] newArray(int size) {
            return new ShopInfo[size];
        }
    };

    @Override
    public String toString() {
        return "ShopInfo{shopId='" + shopId + "', shopName='" + shopName + "', platform='" + platform + "', extractedAt=" + extractedAt + ", packageName='" + packageName + "', userId=" + userId + "}";
    }
}
