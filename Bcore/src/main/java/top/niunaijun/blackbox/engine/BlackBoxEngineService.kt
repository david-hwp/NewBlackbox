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
            val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
            if (!isLegacyLaunchAuthorized(pkg, userId)) {
                Slog.w(TAG, "legacy launch blocked package=$pkg userId=$userId")
                return null
            }
            return BlackBoxCore.get().getLaunchIntent(pkg, userId)
        }

        override fun launchApk(packageName: String?, userId: Int): Boolean {
            val pkg = packageName?.takeIf { it.isNotBlank() } ?: return false
            if (!isLegacyLaunchAuthorized(pkg, userId)) {
                Slog.w(TAG, "legacy launchApk blocked package=$pkg userId=$userId")
                return false
            }
            return BlackBoxCore.get().launchApk(pkg, userId)
        }

        override fun prepareLaunch(packageName: String?, userId: Int): LaunchPreparationResult {
            val pkg = packageName?.takeIf { it.isNotBlank() }
                ?: return LaunchPreparationResult(false, 0, 0, false, "Package is empty")
            val start = System.currentTimeMillis()
            return try {
                val singleInstance = BlackBoxCore.get().isSingleInstanceModeEnabled
                val killed = BlackBoxCore.get().prepareLaunch(pkg, userId)
                LaunchPreparationResult(
                    singleInstance,
                    killed.coerceAtLeast(0),
                    System.currentTimeMillis() - start,
                    killed >= 0,
                    if (killed < 0) "Failed to prepare launch" else null
                )
            } catch (e: Exception) {
                Slog.e(TAG, "prepareLaunch failed package=$pkg userId=$userId", e)
                LaunchPreparationResult(
                    BlackBoxCore.get().isSingleInstanceModeEnabled,
                    0,
                    System.currentTimeMillis() - start,
                    false,
                    e.message
                )
            }
        }

        override fun peekLaunchIntent(packageName: String?, userId: Int): android.content.Intent? {
            val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
            if (!isLegacyLaunchAuthorized(pkg, userId)) {
                Slog.w(TAG, "legacy peek launch blocked package=$pkg userId=$userId")
                return null
            }
            return BlackBoxCore.get().peekLaunchIntent(pkg, userId)
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

        override fun ensureCloneUser(cloneInstanceId: String?, packageName: String?, serverUserId: Long): Int {
            return CloneInstanceStore.ensureCloneUser(cloneInstanceId, packageName, serverUserId)
        }

        override fun findCloneUserId(cloneInstanceId: String?, packageName: String?, serverUserId: Long): Int {
            return CloneInstanceStore.findCloneUserId(cloneInstanceId, packageName, serverUserId)
        }

        override fun bindCloneUser(cloneInstanceId: String?, packageName: String?, serverUserId: Long, userId: Int) {
            CloneInstanceStore.bindCloneUser(cloneInstanceId, packageName, serverUserId, userId)
        }

        override fun clearCloneUser(cloneInstanceId: String?, packageName: String?, serverUserId: Long) {
            CloneInstanceStore.clearCloneUser(cloneInstanceId, packageName, serverUserId)
        }

        override fun writeCloneAuthorization(
            cloneInstanceId: String?,
            packageName: String?,
            serverUserId: Long,
            phone: String?,
            userId: Int,
            publicKeyId: String?,
            authorizationToken: String?
        ): Boolean {
            return CloneInstanceStore.writeAuthorization(
                cloneInstanceId,
                packageName,
                serverUserId,
                phone,
                userId,
                publicKeyId,
                authorizationToken
            )
        }

        override fun getAuthorizedLaunchIntent(cloneInstanceId: String?, packageName: String?, userId: Int): android.content.Intent? {
            val cloneId = cloneInstanceId?.takeIf { it.isNotBlank() } ?: return null
            val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
            val serverUserId = extractServerUserIdFromAuth(cloneId, pkg, userId)
            if (serverUserId == null || !CloneInstanceStore.isAuthorized(cloneId, pkg, serverUserId, userId)) {
                Slog.w(TAG, "authorized launch blocked clone=$cloneId package=$pkg userId=$userId")
                return null
            }
            val intent = BlackBoxCore.get().peekLaunchIntent(pkg, userId)
            if (intent == null) {
                Slog.w(TAG, "authorized launch has no intent clone=$cloneId package=$pkg userId=$userId")
            } else {
                Slog.d(TAG, "authorized launch intent ok clone=$cloneId package=$pkg userId=$userId")
            }
            return intent
        }

        override fun peekAuthorizedLaunchIntent(cloneInstanceId: String?, packageName: String?, userId: Int): android.content.Intent? {
            val cloneId = cloneInstanceId?.takeIf { it.isNotBlank() } ?: return null
            val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
            val serverUserId = extractServerUserIdFromAuth(cloneId, pkg, userId)
            if (serverUserId == null || !CloneInstanceStore.isAuthorized(cloneId, pkg, serverUserId, userId)) {
                Slog.w(TAG, "authorized peek launch blocked clone=$cloneId package=$pkg userId=$userId")
                return null
            }
            return BlackBoxCore.get().peekLaunchIntent(pkg, userId)
        }

        override fun isCloneAuthorized(cloneInstanceId: String?, packageName: String?, serverUserId: Long, userId: Int): Boolean {
            return CloneInstanceStore.isAuthorized(cloneInstanceId, packageName, serverUserId, userId)
        }

        override fun clearClonePackageData(cloneInstanceId: String?, packageName: String?, serverUserId: Long, userId: Int): Boolean {
            return CloneInstanceStore.clearClonePackageData(cloneInstanceId, packageName, serverUserId, userId)
        }

        override fun exportLoginState(packageName: String?, userId: Int, profileId: String?): ByteArray? {
            return LoginStateSyncManager.exportLoginState(packageName, userId, profileId)
        }

        override fun restoreLoginState(packageName: String?, userId: Int, profileId: String?, artifact: ByteArray?): Boolean {
            return LoginStateSyncManager.restoreLoginState(packageName, userId, profileId, artifact)
        }

        override fun defaultLoginStateProfile(packageName: String?): String? {
            return LoginStateSyncManager.defaultProfileId(packageName)
        }

        override fun migrateCloneDataToScopedStorage(): String {
            return CloneInstanceStore.migrateAllScoped().toString()
        }

        private fun isLegacyLaunchAuthorized(packageName: String, userId: Int): Boolean {
            val mapping = CloneInstanceStore.findMappingForPackageUser(packageName, userId)
            return CloneInstanceStore.isAuthorizedMapping(mapping)
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

        override fun triggerShopIdExtract(packageName: String?, userId: Int): ShopInfo? {
            if (packageName.isNullOrBlank()) {
                return null
            }
            return try {
                val info = ShopIdManager.get().extractNow(packageName, userId, ctx)
                if (info != null) {
                    info.packageName = packageName
                    info.userId = userId
                }
                Slog.d(TAG, "triggerShopIdExtract completed for $packageName, userId=$userId, found=${info != null}")
                info
            } catch (e: Exception) {
                Slog.w(TAG, "triggerShopIdExtract failed for $packageName, userId=$userId", e)
                null
            }
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

        private fun extractServerUserIdFromAuth(cloneInstanceId: String, packageName: String, userId: Int): Long? {
            val users = BlackBoxCore.get().getUsers().orEmpty()
            if (users.none { it.id == userId }) {
                return null
            }
            // Server user id is verified again by CloneInstanceStore using the stored meta.
            // The AIDL launch call intentionally does not accept it from APP.
            val meta = java.io.File(
                java.io.File(top.niunaijun.blackbox.core.env.BEnvironment.getSystemDir(), "clone-auth"),
                cloneInstanceId.replace(Regex("[^A-Za-z0-9._-]"), "_") + "/meta.json"
            )
            if (!meta.exists()) {
                return null
            }
            return runCatching {
                val json = org.json.JSONObject(meta.readText())
                if (json.optString("packageName") == packageName &&
                    json.optInt("localVirtualUserId", -1) == userId
                ) {
                    json.optLong("serverUserId", -1L).takeIf { it >= 0 }
                } else {
                    null
                }
            }.getOrNull()
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
