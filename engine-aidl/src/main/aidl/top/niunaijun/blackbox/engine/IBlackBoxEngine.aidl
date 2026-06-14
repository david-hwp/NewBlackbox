package top.niunaijun.blackbox.engine;

import top.niunaijun.blackbox.core.system.pm.IBPackageManagerService;
import top.niunaijun.blackbox.core.system.am.IBActivityManagerService;
import top.niunaijun.blackbox.core.system.location.IBLocationManagerService;
import top.niunaijun.blackbox.core.system.user.IBUserManagerService;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.entity.pm.ShopInfo;
import top.niunaijun.blackbox.entity.pm.WechatShareTarget;
import top.niunaijun.blackbox.core.system.user.BUserInfo;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.entity.location.BLocationConfig;
import top.niunaijun.blackbox.engine.LaunchPreparationResult;
import top.niunaijun.blackbox.engine.IWechatShareCaptureCallback;
import android.content.pm.ApplicationInfo;
import android.content.Intent;

interface IBlackBoxEngine {
    int getVersionCode();
    String getVersionName();

    Intent getLaunchIntent(String packageName, int userId);
    boolean launchApk(String packageName, int userId);
    LaunchPreparationResult prepareLaunch(String packageName, int userId);
    Intent peekLaunchIntent(String packageName, int userId);
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
    ShopInfo triggerShopIdExtract(String packageName, int userId);

    void registerSession(String sessionId, long expireAt);
    void unregisterSession();
    boolean isSessionActive();

    void addServiceAvailableCallback(IBinder callback);

    void configureLogUpload(String endpointUrl, String authToken);

    int ensureCloneUser(String cloneInstanceId, String packageName, long serverUserId);
    int findCloneUserId(String cloneInstanceId, String packageName, long serverUserId);
    void bindCloneUser(String cloneInstanceId, String packageName, long serverUserId, int userId);
    void clearCloneUser(String cloneInstanceId, String packageName, long serverUserId);
    boolean writeCloneAuthorization(String cloneInstanceId, String packageName, long serverUserId, String phone, int userId, String publicKeyId, String authorizationToken);
    Intent getAuthorizedLaunchIntent(String cloneInstanceId, String packageName, int userId);
    Intent peekAuthorizedLaunchIntent(String cloneInstanceId, String packageName, int userId);
    boolean isCloneAuthorized(String cloneInstanceId, String packageName, long serverUserId, int userId);
    boolean clearClonePackageData(String cloneInstanceId, String packageName, long serverUserId, int userId);
    byte[] exportLoginState(String packageName, int userId, String profileId);
    boolean restoreLoginState(String packageName, int userId, String profileId, in byte[] artifact);
    String defaultLoginStateProfile(String packageName);
    String migrateCloneDataToScopedStorage();
    boolean startActivityAsUser(in Intent intent, int userId);
    boolean startWechatShareCapture(String packageName, int userId, IWechatShareCaptureCallback callback, long timeoutMs);
    void cancelWechatShareCapture(String packageName, int userId);
}
