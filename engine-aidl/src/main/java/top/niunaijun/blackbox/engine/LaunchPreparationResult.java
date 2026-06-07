package top.niunaijun.blackbox.engine;

import android.os.Parcel;
import android.os.Parcelable;

public class LaunchPreparationResult implements Parcelable {
    public final boolean singleInstanceMode;
    public final int killedProcessCount;
    public final long elapsedMs;
    public final boolean success;
    public final String message;

    public LaunchPreparationResult(
            boolean singleInstanceMode,
            int killedProcessCount,
            long elapsedMs,
            boolean success,
            String message
    ) {
        this.singleInstanceMode = singleInstanceMode;
        this.killedProcessCount = killedProcessCount;
        this.elapsedMs = elapsedMs;
        this.success = success;
        this.message = message;
    }

    protected LaunchPreparationResult(Parcel in) {
        singleInstanceMode = in.readByte() != 0;
        killedProcessCount = in.readInt();
        elapsedMs = in.readLong();
        success = in.readByte() != 0;
        message = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (singleInstanceMode ? 1 : 0));
        dest.writeInt(killedProcessCount);
        dest.writeLong(elapsedMs);
        dest.writeByte((byte) (success ? 1 : 0));
        dest.writeString(message);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<LaunchPreparationResult> CREATOR = new Creator<LaunchPreparationResult>() {
        @Override
        public LaunchPreparationResult createFromParcel(Parcel in) {
            return new LaunchPreparationResult(in);
        }

        @Override
        public LaunchPreparationResult[] newArray(int size) {
            return new LaunchPreparationResult[size];
        }
    };
}
