package top.niunaijun.blackbox.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;

public final class WechatShareProbe {
    private static final String TAG = "WechatShareProbe";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final int MAX_VALUE_LENGTH = 1600;
    private static final int MAX_UI_TEXTS = 120;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Set<String> DUMPED_UI_KEYS = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private static volatile String sLastSelectConvUser;

    private WechatShareProbe() {
    }

    public static void logActivityIntent(String stage, String activityClass, Intent intent) {
        if (!shouldProbe(activityClass, intent)) {
            return;
        }
        logIntent(stage + " activity=" + activityClass, intent, safeUserId());
    }

    public static void scheduleActivityTextDump(final Activity activity, final String stage) {
        if (activity == null || !shouldProbe(activity.getClass().getName(), activity.getIntent())) {
            return;
        }
        String activityClass = activity.getClass().getName();
        if (!activityClass.contains("SelectConversationUI") && !activityClass.contains("SendAppMessageWrapperUI")) {
            return;
        }
        final String key = activityClass + "@" + System.identityHashCode(activity) + "#" + stage;
        if (!DUMPED_UI_KEYS.add(key)) {
            return;
        }
        MAIN_HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                dumpActivityTexts(activity, stage + "/300ms");
            }
        }, 300);
        MAIN_HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                dumpActivityTexts(activity, stage + "/1200ms");
            }
        }, 1200);
    }

    public static void logIntent(String stage, Intent intent, int userId) {
        if (!shouldProbe(null, intent)) {
            return;
        }
        try {
            ComponentName component = intent.getComponent();
            String selectedUser = findStringExtra(intent.getExtras(), "Select_Conv_User", 0);
            if (selectedUser != null && selectedUser.length() > 0) {
                sLastSelectConvUser = selectedUser;
                dispatchShareTarget(safeAppPackage(), userId, selectedUser, stage, flatten(component));
            }
            Slog.i(TAG, "stage=" + stage
                    + " userId=" + userId
                    + " sourcePkg=" + safeAppPackage()
                    + " selectedUser=" + sLastSelectConvUser
                    + " action=" + intent.getAction()
                    + " type=" + intent.getType()
                    + " package=" + intent.getPackage()
                    + " component=" + flatten(component)
                    + " data=" + safeString(intent.getDataString())
                    + " flags=0x" + Integer.toHexString(intent.getFlags()));
            logBundle(stage, "extra", intent.getExtras(), 0);
        } catch (Throwable e) {
            Slog.w(TAG, "stage=" + stage + " probe failed: " + e.getMessage(), e);
        }
    }

    private static void dumpActivityTexts(Activity activity, String stage) {
        try {
            if (activity.isFinishing() || activity.getWindow() == null) {
                return;
            }
            View root = activity.getWindow().getDecorView();
            if (root == null) {
                return;
            }
            ArrayList<String> rows = new ArrayList<>();
            collectTexts(root, rows, Collections.newSetFromMap(new IdentityHashMap<View, Boolean>()));
            Slog.i(TAG, "stage=" + stage
                    + " activity=" + activity.getClass().getName()
                    + " selectedUser=" + sLastSelectConvUser
                    + " uiTextCount=" + rows.size());
            int limit = Math.min(rows.size(), MAX_UI_TEXTS);
            for (int i = 0; i < limit; i++) {
                Slog.i(TAG, "stage=" + stage + " uiText[" + i + "]=" + rows.get(i));
            }
            if (rows.size() > limit) {
                Slog.i(TAG, "stage=" + stage + " uiText=<truncated:" + rows.size() + ">");
            }
        } catch (Throwable e) {
            Slog.w(TAG, "stage=" + stage + " ui dump failed: " + e.getMessage(), e);
        }
    }

    private static void collectTexts(View view, ArrayList<String> rows, Set<View> visited) {
        if (view == null || rows.size() >= MAX_UI_TEXTS || !visited.add(view)) {
            return;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            CharSequence hint = textView.getHint();
            CharSequence description = textView.getContentDescription();
            String textString = normalize(text);
            String hintString = normalize(hint);
            String descriptionString = normalize(description);
            if (hasText(textString) || hasText(hintString) || hasText(descriptionString)) {
                rows.add("class=" + view.getClass().getName()
                        + " id=" + viewId(view)
                        + " visible=" + view.getVisibility()
                        + " text=" + textString
                        + " hint=" + hintString
                        + " desc=" + descriptionString);
            }
        } else {
            CharSequence description = view.getContentDescription();
            String descriptionString = normalize(description);
            if (hasText(descriptionString)) {
                rows.add("class=" + view.getClass().getName()
                        + " id=" + viewId(view)
                        + " visible=" + view.getVisibility()
                        + " desc=" + descriptionString);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && rows.size() < MAX_UI_TEXTS; i++) {
                collectTexts(group.getChildAt(i), rows, visited);
            }
        }
    }

    private static String viewId(View view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return "none";
        }
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            return "0x" + Integer.toHexString(id);
        }
    }

    private static String normalize(CharSequence value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() == 0) {
            return null;
        }
        return trim(text);
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static boolean shouldProbe(String activityClass, Intent intent) {
        String appPackage = safeAppPackage();
        ComponentName component = intent == null ? null : intent.getComponent();
        String componentPackage = component == null ? null : component.getPackageName();
        String componentClass = component == null ? activityClass : component.getClassName();
        boolean fromWechat = WECHAT_PACKAGE.equals(appPackage)
                || WECHAT_PACKAGE.equals(componentPackage)
                || (intent != null && WECHAT_PACKAGE.equals(intent.getPackage()));
        if (!fromWechat) {
            return false;
        }
        if (componentClass != null) {
            String name = componentClass.toLowerCase();
            if (name.contains("share")
                    || name.contains("transmit")
                    || name.contains("selectconversation")
                    || name.contains("sendappmessage")) {
                return true;
            }
        }
        if (intent != null) {
            String action = intent.getAction();
            if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                return true;
            }
            Bundle extras = safeExtras(intent);
            if (extras != null) {
                try {
                    for (String key : extras.keySet()) {
                        if (isWechatShareKey(key)) {
                            return true;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static boolean isWechatShareKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("select_conv")
                || lower.contains("select_contact")
                || lower.contains("sendappmessage")
                || lower.contains("retr_msg")
                || lower.contains("touser")
                || lower.contains("username")
                || lower.contains("talker")
                || lower.contains("chatroom");
    }

    private static String findStringExtra(Bundle bundle, String targetKey, int depth) {
        if (bundle == null || depth > 3) {
            return null;
        }
        try {
            bundle.setClassLoader(WechatShareProbe.class.getClassLoader());
            for (String key : bundle.keySet()) {
                Object value;
                try {
                    value = bundle.get(key);
                } catch (Throwable ignored) {
                    continue;
                }
                if (targetKey.equals(key) && value instanceof String) {
                    return (String) value;
                }
                if (value instanceof Bundle) {
                    String nested = findStringExtra((Bundle) value, targetKey, depth + 1);
                    if (nested != null) {
                        return nested;
                    }
                } else if (value instanceof Intent) {
                    String nested = findStringExtra(((Intent) value).getExtras(), targetKey, depth + 1);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void logBundle(String stage, String prefix, Bundle bundle, int depth) {
        if (bundle == null) {
            Slog.i(TAG, "stage=" + stage + " " + prefix + "=<none>");
            return;
        }
        if (depth > 2) {
            Slog.i(TAG, "stage=" + stage + " " + prefix + "=<max-depth>");
            return;
        }
        try {
            bundle.setClassLoader(WechatShareProbe.class.getClassLoader());
            Set<String> keys = bundle.keySet();
            if (keys.isEmpty()) {
                Slog.i(TAG, "stage=" + stage + " " + prefix + "=<empty>");
                return;
            }
            for (String key : keys) {
                Object value;
                try {
                    value = bundle.get(key);
                } catch (Throwable e) {
                    Slog.i(TAG, "stage=" + stage + " " + prefix + "[" + key + "]=<unreadable:" + e.getClass().getSimpleName() + ">");
                    continue;
                }
                logValue(stage, prefix + "[" + key + "]", value, depth);
            }
        } catch (Throwable e) {
            Slog.i(TAG, "stage=" + stage + " " + prefix + "=<unreadable:" + e.getClass().getSimpleName() + ">");
        }
    }

    private static void logValue(String stage, String name, Object value, int depth) {
        if (value == null) {
            Slog.i(TAG, "stage=" + stage + " " + name + "=<null>");
            return;
        }
        if (value instanceof Bundle) {
            logBundle(stage, name, (Bundle) value, depth + 1);
            return;
        }
        if (value instanceof Intent) {
            Intent nested = (Intent) value;
            Slog.i(TAG, "stage=" + stage + " " + name + "=Intent{action="
                    + nested.getAction() + ", component=" + flatten(nested.getComponent())
                    + ", package=" + nested.getPackage() + ", type=" + nested.getType() + "}");
            logBundle(stage, name + ".extra", nested.getExtras(), depth + 1);
            return;
        }
        if (value.getClass().isArray()) {
            Slog.i(TAG, "stage=" + stage + " " + name + "=" + formatArray(value));
            return;
        }
        if (value instanceof ArrayList) {
            Slog.i(TAG, "stage=" + stage + " " + name + "=" + formatArrayList((ArrayList<?>) value));
            return;
        }
        if (value instanceof Parcelable) {
            Slog.i(TAG, "stage=" + stage + " " + name + "="
                    + value.getClass().getName() + "{" + trim(String.valueOf(value)) + "}");
            return;
        }
        Slog.i(TAG, "stage=" + stage + " " + name + "="
                + value.getClass().getName() + "{" + trim(String.valueOf(value)) + "}");
    }

    private static String formatArray(Object value) {
        int length = Array.getLength(value);
        StringBuilder builder = new StringBuilder();
        builder.append(value.getClass().getComponentType()).append("[").append(length).append("]{");
        int limit = Math.min(length, 20);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Object item = Array.get(value, i);
            builder.append(trim(String.valueOf(item)));
        }
        if (length > limit) {
            builder.append(", ...");
        }
        builder.append("}");
        return builder.toString();
    }

    private static String formatArrayList(ArrayList<?> value) {
        StringBuilder builder = new StringBuilder();
        builder.append("ArrayList[").append(value.size()).append("]{");
        int limit = Math.min(value.size(), 20);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            Object item = value.get(i);
            builder.append(item == null ? "<null>" : item.getClass().getName() + "{" + trim(String.valueOf(item)) + "}");
        }
        if (value.size() > limit) {
            builder.append(", ...");
        }
        builder.append("}");
        return builder.toString();
    }

    private static Bundle safeExtras(Intent intent) {
        try {
            return intent.getExtras();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeAppPackage() {
        try {
            return BActivityThread.getAppPackageName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int safeUserId() {
        try {
            return BActivityThread.getUserId();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String flatten(ComponentName component) {
        return component == null ? null : component.flattenToShortString();
    }

    private static void dispatchShareTarget(String packageName, int userId, String receiverId, String stage, String component) {
        try {
            BlackBoxCore.getBActivityManager().dispatchWechatShareTarget(packageName, userId, receiverId, stage, component);
        } catch (Throwable e) {
            Slog.w(TAG, "dispatch share target failed: " + e.getMessage(), e);
        }
    }

    private static String safeString(String value) {
        return value == null ? null : trim(value);
    }

    private static String trim(String value) {
        if (value == null || value.length() <= MAX_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_VALUE_LENGTH) + "...<trimmed:" + value.length() + ">";
    }
}
