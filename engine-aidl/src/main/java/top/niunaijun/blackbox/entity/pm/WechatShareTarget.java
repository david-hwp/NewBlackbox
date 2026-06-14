package top.niunaijun.blackbox.entity.pm;

import android.os.Parcel;
import android.os.Parcelable;

public class WechatShareTarget implements Parcelable {
    public static final String TYPE_GROUP = "group";
    public static final String TYPE_CONTACT = "contact";
    public static final String TYPE_UNKNOWN = "unknown";

    public String receiverId;
    public String receiverName;
    public String receiverType;
    public String packageName;
    public int userId;
    public long capturedAt;
    public String evidence;

    public WechatShareTarget() {
    }

    public WechatShareTarget(String receiverId, String receiverName, String receiverType,
                             String packageName, int userId, long capturedAt, String evidence) {
        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.receiverType = receiverType;
        this.packageName = packageName;
        this.userId = userId;
        this.capturedAt = capturedAt;
        this.evidence = evidence;
    }

    protected WechatShareTarget(Parcel in) {
        receiverId = in.readString();
        receiverName = in.readString();
        receiverType = in.readString();
        packageName = in.readString();
        userId = in.readInt();
        capturedAt = in.readLong();
        evidence = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(receiverId);
        dest.writeString(receiverName);
        dest.writeString(receiverType);
        dest.writeString(packageName);
        dest.writeInt(userId);
        dest.writeLong(capturedAt);
        dest.writeString(evidence);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WechatShareTarget> CREATOR = new Creator<WechatShareTarget>() {
        @Override
        public WechatShareTarget createFromParcel(Parcel in) {
            return new WechatShareTarget(in);
        }

        @Override
        public WechatShareTarget[] newArray(int size) {
            return new WechatShareTarget[size];
        }
    };

    @Override
    public String toString() {
        return "WechatShareTarget{receiverId='" + receiverId + "', receiverName='" + receiverName
                + "', receiverType='" + receiverType + "', packageName='" + packageName
                + "', userId=" + userId + ", capturedAt=" + capturedAt
                + ", evidence='" + evidence + "'}";
    }
}
