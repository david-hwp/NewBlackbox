package top.niunaijun.blackbox.engine;

import top.niunaijun.blackbox.core.system.pm.IBPackageManagerService;
import top.niunaijun.blackbox.core.system.am.IBActivityManagerService;
import top.niunaijun.blackbox.core.system.location.IBLocationManagerService;
import top.niunaijun.blackbox.core.system.user.IBUserManagerService;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.core.system.user.BUserInfo;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.entity.location.BLocationConfig;
import android.content.pm.ApplicationInfo;
import android.content.Intent;

interface IBlackBoxEngine {
    int getVersionCode();
    String getVersionName();

    Intent getLaunchIntent(String packageName, int userId);
    boolean launchApk(String packageName, int userId);
    InstallResult installPackageAsUser(String path, int userId);
    void uninstallPackageAsUser(String packageName, int userId);
    List<ApplicationInfo> getInstalledApplications(int flags, int userId);
    List<BUserInfo> getUsers();
    BUserInfo createUser(int userId);
    void deleteUser(int userId);
    boolean isInstalled(String packageName, int userId);
    oneway void clearPackage(String packageName, int userId);
    oneway void stopPackage(String packageName, int userId);

    IBPackageManagerService getPackageManager();
    IBActivityManagerService getActivityManager();
    IBLocationManagerService getLocationManager();
    IBUserManagerService getUserManager();

    boolean isSupportGms();
    boolean isInstallGms(int userId);
    InstallResult installGms(int userId);
    boolean uninstallGms(int userId);

    oneway void sendLogs(String caption, boolean async);
    oneway void sendLogsToEndpoint(String caption, boolean async, String endpointUrl, String authToken);

    ShopInfo getShopInfo(String packageName, int userId);
    oneway void triggerShopIdExtract(String packageName, int userId);
    List<ShopInfo> refreshShopInfoByPlatform(String platform, String packageName);

    void registerSession(String sessionId, long expireAt);
    void unregisterSession();
    boolean isSessionActive();

    void addServiceAvailableCallback(IBinder callback);

    void configureLogUpload(String endpointUrl, String authToken);
}
