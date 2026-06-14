package top.niunaijun.blackbox.core.system.am;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import top.niunaijun.blackbox.entity.pm.WechatShareTarget;

public final class WechatShareTargetDispatcher {
    public interface Listener {
        void onWechatShareTargetCaptured(WechatShareTarget target);
    }

    private static final String TYPE_GROUP_SUFFIX = "@chatroom";
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private WechatShareTargetDispatcher() {
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void dispatch(String packageName, int userId, String receiverId, String stage, String component) {
        if (packageName == null || packageName.length() == 0 || userId < 0 || receiverId == null || receiverId.length() == 0) {
            return;
        }
        String type = receiverId.endsWith(TYPE_GROUP_SUFFIX)
                ? WechatShareTarget.TYPE_GROUP
                : WechatShareTarget.TYPE_CONTACT;
        WechatShareTarget target = new WechatShareTarget(
                receiverId,
                null,
                type,
                packageName,
                userId,
                System.currentTimeMillis(),
                "intent:" + safe(stage) + ":" + safe(component)
        );
        for (Listener listener : LISTENERS) {
            listener.onWechatShareTargetCaptured(target);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
