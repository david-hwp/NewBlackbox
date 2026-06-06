package top.niunaijun.blackbox.engine

import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.system.ServiceManager
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService
import top.niunaijun.blackbox.core.system.pm.ShopIdManager
import top.niunaijun.blackbox.core.system.am.BActivityManagerService
import top.niunaijun.blackbox.core.system.location.BLocationManagerService
import top.niunaijun.blackbox.core.system.user.BUserManagerService
import top.niunaijun.blackbox.entity.pm.InstallResult
import top.niunaijun.blackbox.entity.pm.ShopInfo
import top.niunaijun.blackbox.core.system.user.BUserInfo
import top.niunaijun.blackbox.entity.AppConfig
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackbox.entity.location.BLocationConfig
import top.niunaijun.blackbox.utils.Slog
import java.security.MessageDigest
import android.content.pm.ApplicationInfo

class BlackBoxEngineService : Service() {
    companion object {
        const val TAG = "BlackBoxEngineService"
        const val TRUSTED_HOST_PACKAGE = "top.niunaijun.blackbox"

        // SHA-256 fingerprints of trusted host app signatures
        // These should be replaced with actual host APK signatures
        val TRUSTED_FINGERPRINTS = arrayOf(
            // Placeholder - replace with actual host APK SHA-256 signature
            "REPLACE_WITH_ACTUAL_HOST_SIGNATURE"
        )
    }

    private val mBinder = object : IBlackBoxEngine.Stub() {
        // Use applicationContext to avoid name collision with IBlackBoxEngine.getPackageManager()
        private val ctx get() = this@BlackBoxEngineService.applicationContext

        override fun getVersionCode(): Int {
            return try {
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pi.longVersionCode.toInt()
                } else {
                    pi.versionCode
                }
            } catch (e: Exception) {
                0
            }
        }

        override fun getVersionName(): String {
            return try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0)?.versionName ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }

        override fun getLaunchIntent(packageName: String?, userId: Int): android.content.Intent? {
            return if (packageName != null) {
                BlackBoxCore.get().getLaunchIntent(packageName, userId)
            } else null
        }

        override fun launchApk(packageName: String?, userId: Int): Boolean {
            return if (packageName != null) {
                BlackBoxCore.get().launchApk(packageName, userId)
            } else false
        }

        override fun installPackageAsUser(path: String?, userId: Int): InstallResult {
            return if (path != null) {
                try {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        // It's a file path
                        BlackBoxCore.get().installPackageAsUser(file, userId)
                    } else {
                        // Try as package name (original behavior)
                        BlackBoxCore.get().installPackageAsUser(path, userId)
                    }
                } catch (e: Exception) {
                    InstallResult().installError(e.message ?: "Install error")
                }
            } else InstallResult().installError("Path is null")
        }

        override fun uninstallPackageAsUser(packageName: String?, userId: Int) {
            if (packageName != null) {
                BlackBoxCore.get().uninstallPackageAsUser(packageName, userId)
            }
        }

        override fun getInstalledApplications(flags: Int, userId: Int): MutableList<ApplicationInfo> {
            return BlackBoxCore.get().getInstalledApplications(flags, userId) ?: mutableListOf()
        }

        override fun getUsers(): MutableList<BUserInfo> {
            return BlackBoxCore.get().getUsers() ?: mutableListOf()
        }

        override fun createUser(userId: Int): BUserInfo? {
            return BlackBoxCore.get().createUser(userId)
        }

        override fun deleteUser(userId: Int) {
            BlackBoxCore.get().deleteUser(userId)
        }

        override fun isInstalled(packageName: String?, userId: Int): Boolean {
            return if (packageName != null) {
                BlackBoxCore.get().isInstalled(packageName, userId)
            } else false
        }

        override fun clearPackage(packageName: String?, userId: Int) {
            if (packageName != null) {
                BlackBoxCore.get().clearPackage(packageName, userId)
            }
        }

        override fun stopPackage(packageName: String?, userId: Int) {
            if (packageName != null) {
                BlackBoxCore.get().stopPackage(packageName, userId)
            }
        }

        override fun getPackageManager(): top.niunaijun.blackbox.core.system.pm.IBPackageManagerService? {
            val binder = ServiceManager.getService(ServiceManager.PACKAGE_MANAGER)
            return top.niunaijun.blackbox.core.system.pm.IBPackageManagerService.Stub.asInterface(binder)
        }

        override fun getActivityManager(): top.niunaijun.blackbox.core.system.am.IBActivityManagerService? {
            val binder = ServiceManager.getService(ServiceManager.ACTIVITY_MANAGER)
            return top.niunaijun.blackbox.core.system.am.IBActivityManagerService.Stub.asInterface(binder)
        }

        override fun getLocationManager(): top.niunaijun.blackbox.core.system.location.IBLocationManagerService? {
            val binder = ServiceManager.getService(ServiceManager.LOCATION_MANAGER)
            return top.niunaijun.blackbox.core.system.location.IBLocationManagerService.Stub.asInterface(binder)
        }

        override fun getUserManager(): top.niunaijun.blackbox.core.system.user.IBUserManagerService? {
            val binder = ServiceManager.getService(ServiceManager.USER_MANAGER)
            return top.niunaijun.blackbox.core.system.user.IBUserManagerService.Stub.asInterface(binder)
        }

        override fun isSupportGms(): Boolean {
            return BlackBoxCore.get().isSupportGms()
        }

        override fun isInstallGms(userId: Int): Boolean {
            return BlackBoxCore.get().isInstallGms(userId)
        }

        override fun installGms(userId: Int): InstallResult {
            return BlackBoxCore.get().installGms(userId)
        }

        override fun uninstallGms(userId: Int): Boolean {
            return BlackBoxCore.get().uninstallGms(userId)
        }

        override fun configureLogUpload(endpointUrl: String?, authToken: String?) {
            BlackBoxCore.get().configureLogUpload(endpointUrl, authToken)
        }

        override fun sendLogs(caption: String?, async: Boolean) {
            BlackBoxCore.get().sendLogs(caption ?: "", async)
        }

        override fun sendLogsToEndpoint(caption: String?, async: Boolean, endpointUrl: String?, authToken: String?) {
            BlackBoxCore.get().sendLogs(caption ?: "", async, endpointUrl, authToken, null)
        }

        override fun getShopInfo(packageName: String?, userId: Int): ShopInfo? {
            return try {
                val pm = BPackageManagerService.get()
                pm.getShopInfo(packageName ?: "", userId)
            } catch (e: Exception) {
                Slog.w(TAG, "getShopInfo failed: ${e.message}")
                null
            }
        }

        override fun triggerShopIdExtract(packageName: String?, userId: Int) {
            if (packageName.isNullOrBlank()) {
                return
            }
            try {
                ShopIdManager.get().triggerExtract(packageName, userId, ctx)
                Slog.d(TAG, "triggerShopIdExtract requested for $packageName, userId=$userId")
            } catch (e: Exception) {
                Slog.w(TAG, "triggerShopIdExtract failed for $packageName, userId=$userId", e)
            }
        }

        override fun refreshShopInfoByPlatform(platform: String?, packageName: String?): MutableList<ShopInfo> {
            val targetPackage = packageName?.takeIf { it.isNotBlank() } ?: return mutableListOf()
            val targetPlatform = platform?.takeIf { it.isNotBlank() }
            val result = mutableListOf<ShopInfo>()
            try {
                BlackBoxCore.get().getUsers().orEmpty().forEach { user ->
                    val userId = user.id
                    if (!BlackBoxCore.get().isInstalled(targetPackage, userId)) {
                        return@forEach
                    }
                    val shopInfo = ShopIdManager.get().extractNow(targetPackage, userId, ctx)
                    if (shopInfo?.shopId.isNullOrBlank()) {
                        return@forEach
                    }
                    if (!targetPlatform.isNullOrBlank() && shopInfo.platform != targetPlatform) {
                        return@forEach
                    }
                    shopInfo.packageName = targetPackage
                    shopInfo.userId = userId
                    result.add(shopInfo)
                }
                Slog.d(TAG, "refreshShopInfoByPlatform platform=$platform package=$targetPackage size=${result.size}")
            } catch (e: Exception) {
                Slog.w(TAG, "refreshShopInfoByPlatform failed platform=$platform package=$targetPackage", e)
            }
            return result
        }

        override fun registerSession(sessionId: String?, expireAt: Long) {
            // Session management - to be implemented in Wave 2
            Slog.d(TAG, "registerSession: $sessionId, expireAt: $expireAt")
        }

        override fun unregisterSession() {
            // Session management - to be implemented in Wave 2
            Slog.d(TAG, "unregisterSession")
        }

        override fun isSessionActive(): Boolean {
            // Session management - to be implemented in Wave 2
            return true
        }

        override fun addServiceAvailableCallback(callback: IBinder?) {
            if (callback != null) {
                BlackBoxCore.get().addServiceAvailableCallback {
                    try {
                        // Notify the callback via a simple transaction
                        callback.transact(1, android.os.Parcel.obtain(), null, 0)
                    } catch (e: Exception) {
                        Slog.w(TAG, "Service available callback failed: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onBind(intent: android.content.Intent?): IBinder? {
        val callingUid = Binder.getCallingUid()

        // Note: During early engine initialization, PackageManager hooks may not
        // be fully ready. We skip getPackagesForUid() here to avoid NPE.
        // The BIND_ENGINE permission declared in AndroidManifest already ensures
        // only the trusted host app can bind to this service.
        Slog.d(TAG, "onBind called from uid: $callingUid")

        // Optional: verify signature fingerprint
        // In production, also verify callingPackage == TRUSTED_HOST_PACKAGE
        // val callingPackage = packageManager.getPackagesForUid(callingUid)?.firstOrNull()
        // if (callingPackage != TRUSTED_HOST_PACKAGE) return null

        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        Slog.d(TAG, "BlackBoxEngineService onCreate")
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        Slog.d(TAG, "BlackBoxEngineService onStartCommand")
        return START_STICKY
    }

    private fun verifyCallerSignature(packageName: String): Boolean {
        return try {
            if (TRUSTED_FINGERPRINTS[0] == "REPLACE_WITH_ACTUAL_HOST_SIGNATURE") {
                // During development, skip strict signature check
                return true
            }

            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            val signatures = packageInfo.signatures

            if (signatures != null) {
                for (signature in signatures) {
                    val fingerprint = getSignatureFingerprint(signature)
                    if (fingerprint != null && TRUSTED_FINGERPRINTS.contains(fingerprint)) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Slog.e(TAG, "Signature verification error", e)
            false
        }
    }

    private fun getSignatureFingerprint(signature: Signature): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signature.toByteArray())
            val sb = StringBuilder()
            for (b in digest) {
                sb.append(String.format("%02X", b))
            }
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }
}
