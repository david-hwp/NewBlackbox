package top.niunaijun.blackbox.core.system.pm;

import android.os.Parcel;
import android.os.Parcelable;


public class BPackageUserState implements Parcelable {
    public boolean installed;
    public boolean stopped;
    public boolean hidden;
    public String shopId;
    public String shopName;
    public String platform;

    public BPackageUserState() {
        this.installed = false;
        this.stopped = true;
        this.hidden = false;
        this.shopId = null;
        this.shopName = null;
        this.platform = null;
    }

    public static BPackageUserState create() {
        BPackageUserState state = new BPackageUserState();
        state.installed = true;
        return state;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.installed ? (byte) 1 : (byte) 0);
        dest.writeByte(this.stopped ? (byte) 1 : (byte) 0);
        dest.writeByte(this.hidden ? (byte) 1 : (byte) 0);
        dest.writeString(this.shopId);
        dest.writeString(this.shopName);
        dest.writeString(this.platform);
    }

    protected BPackageUserState(Parcel in) {
        this.installed = in.readByte() != 0;
        this.stopped = in.readByte() != 0;
        this.hidden = in.readByte() != 0;
        this.shopId = in.readString();
        this.shopName = in.readString();
        this.platform = in.readString();
    }

    public BPackageUserState(BPackageUserState state) {
        this.installed = state.installed;
        this.stopped = state.stopped;
        this.hidden = state.hidden;
        this.shopId = state.shopId;
        this.shopName = state.shopName;
        this.platform = state.platform;
    }

    public static final Parcelable.Creator<BPackageUserState> CREATOR = new Parcelable.Creator<BPackageUserState>() {
        @Override
        public BPackageUserState createFromParcel(Parcel source) {
            return new BPackageUserState(source);
        }

        @Override
        public BPackageUserState[] newArray(int size) {
            return new BPackageUserState[size];
        }
    };
}
