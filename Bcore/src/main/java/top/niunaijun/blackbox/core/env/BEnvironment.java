package top.niunaijun.blackbox.core.env;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

import top.niunaijun.blackbox.BuildConfig;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;

public class BEnvironment {
    private static final String TAG = "BEnvironment";
    private static final String FALLBACK_ENGINE_ROOT_NAME = "blackbox";
    private static volatile File sVirtualRoot;
    private static volatile File sExternalVirtualRoot;

    public static File JUNIT_JAR = new File(getCacheDir(), "junit.apk");
    public static File EMPTY_JAR = new File(getCacheDir(), "empty.apk");

    public static void load() {
        FileUtils.mkdirs(getVirtualRoot());
        FileUtils.mkdirs(getExternalVirtualRoot());
        FileUtils.mkdirs(getSystemDir());
        FileUtils.mkdirs(getCacheDir());
        FileUtils.mkdirs(getProcDir());
        Slog.i(TAG, "Resolved engine data roots: " + describeRoots());
    }

    public static File getVirtualRoot() {
        if (sVirtualRoot == null) {
            synchronized (BEnvironment.class) {
                if (sVirtualRoot == null) {
                    sVirtualRoot = new File(getPrivateDataRoot(), resolveRootName());
                }
            }
        }
        return sVirtualRoot;
    }

    public static File getExternalVirtualRoot() {
        if (sExternalVirtualRoot == null) {
            synchronized (BEnvironment.class) {
                if (sExternalVirtualRoot == null) {
                    File externalRoot = BlackBoxCore.getContext().getExternalFilesDir(resolveRootName());
                    sExternalVirtualRoot = externalRoot != null
                            ? externalRoot
                            : new File(getVirtualRoot(), "external");
                }
            }
        }
        return sExternalVirtualRoot;
    }

    public static String describeRoots() {
        Context context = BlackBoxCore.getContext();
        return "package=" + context.getPackageName()
                + ", privateDataRoot=" + getPrivateDataRoot().getAbsolutePath()
                + ", virtualRoot=" + getVirtualRoot().getAbsolutePath()
                + ", externalVirtualRoot=" + getExternalVirtualRoot().getAbsolutePath();
    }

    public static File getSystemDir() {
        return new File(getVirtualRoot(), "system");
    }

    public static File getProcDir() {
        return new File(getVirtualRoot(), "proc");
    }

    public static File getCacheDir() {
        return new File(getVirtualRoot(), "cache");
    }

    public static File getUserInfoConf() {
        return new File(getSystemDir(), "user.conf");
    }

    public static File getAccountsConf() {
        return new File(getSystemDir(), "accounts.conf");
    }

    public static File getUidConf() {
        return new File(getSystemDir(), "uid.conf");
    }

    public static File getSharedUserConf() {
        return new File(getSystemDir(), "shared-user.conf");
    }

    public static File getXPModuleConf() {
        return new File(getSystemDir(), "xposed-module.conf");
    }

    public static File getFakeLocationConf() {
        return new File(getSystemDir(), "fake-location.conf");
    }

    public static File getPackageConf(String packageName) {
        return new File(getAppDir(packageName), "package.conf");
    }

    public static File getExternalUserDir(int userId) {
        return getLegacyExternalUserDir(userId);
    }

    public static File getExternalUserDir(String packageName, int userId) {
        return getRequiredScopedExternalUserDir(packageName, userId);
    }

    public static File getUserDir(int userId) {
        return getLegacyUserDir(userId);
    }

    public static File getExternalObbDir(String packageName, int userId) {
        return new File(getExternalUserDir(packageName, userId), String.format(Locale.CHINA, "Android/obb/%s", packageName));
    }

    public static File getDeDataDir(String packageName, int userId) {
        return getRequiredScopedDataDir(packageName, userId, "user_de");
    }

    public static File getExternalDataDir(String packageName, int userId) {
        return getRequiredScopedExternalDataDir(packageName, userId);
    }

    public static File getDataDir(String packageName, int userId) {
        return getRequiredScopedDataDir(packageName, userId, "user");
    }

    public static File getLegacyUserDir(int userId) {
        return new File(getVirtualRoot(), String.format(Locale.CHINA, "data/user/%d", userId));
    }

    public static File getLegacyDataDir(String packageName, int userId) {
        return new File(getVirtualRoot(), String.format(Locale.CHINA, "data/user/%d/%s", userId, packageName));
    }

    public static File getLegacyDeDataDir(String packageName, int userId) {
        return new File(getVirtualRoot(), String.format(Locale.CHINA, "data/user_de/%d/%s", userId, packageName));
    }

    public static File getLegacyExternalUserDir(int userId) {
        return new File(getExternalVirtualRoot(), String.format(Locale.CHINA, "storage/emulated/%d/", userId));
    }

    public static File getLegacyExternalDataDir(String packageName, int userId) {
        return new File(getLegacyExternalUserDir(userId), String.format(Locale.CHINA, "Android/data/%s", packageName));
    }

    public static File getProcDir(int pid) {
        File file = new File(getProcDir(), String.format(Locale.CHINA, "%d", pid));
        FileUtils.mkdirs(file);
        return file;
    }

    public static File getExternalDataFilesDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "files");
    }

    public static File getDataFilesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "files");
    }

    public static File getExternalDataCacheDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "cache");
    }

    public static File getDataCacheDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "cache");
    }

    public static File getDataLibDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "lib");
    }

    public static File getDataDatabasesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "databases");
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getAppDir(String packageName) {
        return new File(getVirtualRoot(), "data/app/" + packageName);
    }

    public static File getBaseApkDir(String packageName) {
        return new File(getVirtualRoot(), "data/app/" + packageName + "/base.apk");
    }

    public static File getAppLibDir(String packageName) {
        return new File(getAppDir(packageName), "lib");
    }

    public static File getXSharedPreferences(String packageName, String prefFileName) {
       return getXSharedPreferences(packageName, BlackBoxCore.getUserId(), prefFileName);
    }

    public static File getXSharedPreferences(String packageName, int userId, String prefFileName) {
       return new File(BEnvironment.getDataDir(packageName, userId), "shared_prefs/" + prefFileName + ".xml");
    }

    private static File getRequiredScopedDataDir(String packageName, int userId, String userDirName) {
        JSONObject mapping = findCloneMapping(packageName, userId);
        if (mapping == null) {
            throw new IllegalStateException("Missing clone mapping for package=" + packageName + ", userId=" + userId);
        }
        String cloneId = mapping.optString("cloneInstanceId");
        long serverUserId = mapping.optLong("serverUserId", -1L);
        if (cloneId.isEmpty() || serverUserId < 0) {
            throw new IllegalStateException("Invalid clone mapping for package=" + packageName + ", userId=" + userId);
        }
        return new File(cardRoot(serverUserId, cloneId), userDirName + "/" + userId + "/" + packageName);
    }

    private static File getRequiredScopedExternalUserDir(String packageName, int userId) {
        JSONObject mapping = findCloneMapping(packageName, userId);
        if (mapping == null) {
            throw new IllegalStateException("Missing clone mapping for package=" + packageName + ", userId=" + userId);
        }
        String cloneId = mapping.optString("cloneInstanceId");
        long serverUserId = mapping.optLong("serverUserId", -1L);
        if (cloneId.isEmpty() || serverUserId < 0) {
            throw new IllegalStateException("Invalid clone mapping for package=" + packageName + ", userId=" + userId);
        }
        return new File(externalCardRoot(serverUserId, cloneId), "storage/emulated/" + userId);
    }

    private static File getRequiredScopedExternalDataDir(String packageName, int userId) {
        return new File(getRequiredScopedExternalUserDir(packageName, userId), "Android/data/" + packageName);
    }

    private static JSONObject findCloneMapping(String packageName, int userId) {
        if (packageName == null || packageName.trim().isEmpty() || userId < 0) {
            return null;
        }
        File file = new File(getSystemDir(), "clone-instances.json");
        if (!file.exists()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(readText(file));
            java.util.Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                JSONObject mapping = root.optJSONObject(keys.next());
                if (mapping == null) {
                    continue;
                }
                if (packageName.equals(mapping.optString("packageName")) &&
                        userId == mapping.optInt("userId", -1)) {
                    return mapping;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String readText(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
        }
        return out.toString("UTF-8");
    }

    private static File cardRoot(long serverUserId, String cloneInstanceId) {
        return new File(getVirtualRoot(), "accounts/" + safeName(String.valueOf(serverUserId)) + "/cards/" + safeName(cloneInstanceId));
    }

    private static File externalCardRoot(long serverUserId, String cloneInstanceId) {
        return new File(getExternalVirtualRoot(), "accounts/" + safeName(String.valueOf(serverUserId)) + "/cards/" + safeName(cloneInstanceId));
    }

    private static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static File getPrivateDataRoot() {
        Context context = BlackBoxCore.getContext();
        File cacheDir = context.getCacheDir();
        File privateRoot = cacheDir != null ? cacheDir.getParentFile() : null;
        if (privateRoot != null) {
            return privateRoot;
        }
        File filesDir = context.getFilesDir();
        privateRoot = filesDir != null ? filesDir.getParentFile() : null;
        if (privateRoot != null) {
            return privateRoot;
        }
        return context.getDir("engine-data-root", Context.MODE_PRIVATE);
    }

    private static String resolveRootName() {
        String rootName = BuildConfig.ENGINE_DATA_ROOT_NAME.trim();
        if (rootName.length() == 0 || rootName.contains("/") || ".".equals(rootName) || "..".equals(rootName)) {
            return FALLBACK_ENGINE_ROOT_NAME;
        }
        return rootName;
    }
}
