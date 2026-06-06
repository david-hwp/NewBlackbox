package top.niunaijun.blackbox.entity.am;

import android.content.BroadcastReceiver;
import android.os.Bundle;
import android.os.IBinder;

import java.util.UUID;

import black.android.content.BRBroadcastReceiverPendingResult;
import black.android.content.BRBroadcastReceiverPendingResultM;
import black.android.content.BroadcastReceiverPendingResultContext;
import black.android.content.BroadcastReceiverPendingResultMContext;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

/**
 * Helper for PendingResultData to bridge framework-dependent operations.
 * These methods cannot live in engine-aidl because they depend on black-reflection.
 */
public class PendingResultDataHelper {

    public static PendingResultData fromPendingResult(BroadcastReceiver.PendingResult pendingResult) {
        PendingResultData data = new PendingResultData();
        data.mBToken = UUID.randomUUID().toString();
        if (BuildCompat.isM()) {
            BroadcastReceiverPendingResultMContext resultMContext = BRBroadcastReceiverPendingResultM.get(pendingResult);
            data.mType = resultMContext.mType();
            data.mOrderedHint = resultMContext.mOrderedHint();
            data.mInitialStickyHint = resultMContext.mInitialStickyHint();
            data.mToken = resultMContext.mToken();
            data.mSendingUser = resultMContext.mSendingUser();
            data.mFlags = resultMContext.mFlags();
            data.mResultData = resultMContext.mResultData();
            data.mResultExtras = resultMContext.mResultExtras();
            data.mAbortBroadcast = resultMContext.mAbortBroadcast();
            data.mFinished = resultMContext.mFinished();
        } else {
            BroadcastReceiverPendingResultContext resultContext = BRBroadcastReceiverPendingResult.get(pendingResult);
            data.mType = resultContext.mType();
            data.mOrderedHint = resultContext.mOrderedHint();
            data.mInitialStickyHint = resultContext.mInitialStickyHint();
            data.mToken = resultContext.mToken();
            data.mSendingUser = resultContext.mSendingUser();
            data.mResultData = resultContext.mResultData();
            data.mResultExtras = resultContext.mResultExtras();
            data.mAbortBroadcast = resultContext.mAbortBroadcast();
            data.mFinished = resultContext.mFinished();
        }
        return data;
    }

    public static BroadcastReceiver.PendingResult build(PendingResultData data) {
        if (BuildCompat.isM()) {
            return BRBroadcastReceiverPendingResultM.get()._new(
                    data.mResultCode, data.mResultData, data.mResultExtras,
                    data.mType, data.mOrderedHint, data.mInitialStickyHint,
                    data.mToken, data.mSendingUser, data.mFlags);
        } else {
            return BRBroadcastReceiverPendingResult.get()._new(
                    data.mResultCode, data.mResultData, data.mResultExtras,
                    data.mType, data.mOrderedHint, data.mInitialStickyHint,
                    data.mToken, data.mSendingUser);
        }
    }
}
