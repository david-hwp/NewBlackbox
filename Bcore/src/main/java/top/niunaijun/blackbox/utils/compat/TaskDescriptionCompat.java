package top.niunaijun.blackbox.utils.compat;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.utils.DrawableUtils;

public class TaskDescriptionCompat {
    public static ActivityManager.TaskDescription fix(ActivityManager.TaskDescription td) {
        String label = getTaskDescriptionLabel(BlackBoxCore.getUserId(), getApplicationLabel());
        Bitmap icon = getTaskDescriptionIcon();
        return new ActivityManager.TaskDescription(label, icon, td.getPrimaryColor());
    }

    public static String getTaskDescriptionLabel(int userId, CharSequence label) {
        ShopInfo shopInfo = getCurrentShopInfo(userId);
        if (shopInfo != null && shopInfo.shopName != null && !shopInfo.shopName.trim().isEmpty()) {
            return shopInfo.shopName.trim();
        }
        if (label == null) {
            return String.format(Locale.CHINA, "User %d", userId);
        }
        return label.toString();
    }

    public static Bitmap getTaskDescriptionIcon() {
        Drawable drawable = getApplicationIcon();
        if (drawable == null) {
            return null;
        }
        try {
            Context context = BlackBoxCore.getContext();
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            int iconSize = am != null ? am.getLauncherLargeIconSize() : 96;
            return DrawableUtils.drawableToBitmap(drawable, iconSize, iconSize);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static CharSequence getApplicationLabel() {
        try {
            PackageManager pm = BlackBoxCore.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(BlackBoxCore.getAppPackageName(), 0));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Drawable getApplicationIcon() {
        try {
            PackageManager pm = BlackBoxCore.getPackageManager();
            String packageName = BlackBoxCore.getAppPackageName();
            if (packageName == null || packageName.isEmpty()) {
                return null;
            }
            return pm.getApplicationIcon(packageName);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static ShopInfo getCurrentShopInfo(int userId) {
        String packageName = BlackBoxCore.getAppPackageName();
        if (packageName == null || packageName.isEmpty() || userId < 0) {
            return null;
        }
        try {
            return BPackageManager.get().getShopInfo(packageName, userId);
        } catch (Throwable ignore) {
            return null;
        }
    }
}
