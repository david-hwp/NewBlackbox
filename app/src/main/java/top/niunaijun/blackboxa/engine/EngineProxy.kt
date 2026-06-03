package top.niunaijun.blackboxa.engine

import android.content.pm.ApplicationInfo
import android.os.RemoteException
import android.util.Log
import top.niunaijun.blackbox.core.system.am.IBActivityManagerService
import top.niunaijun.blackbox.core.system.location.IBLocationManagerService
import top.niunaijun.blackbox.core.system.pm.IBPackageManagerService
import top.niunaijun.blackbox.core.system.user.BUserInfo
import top.niunaijun.blackbox.core.system.user.IBUserManagerService
import top.niunaijun.blackbox.engine.IBlackBoxEngine
import top.niunaijun.blackbox.entity.pm.InstallResult
import top.niunaijun.blackbox.entity.pm.ShopInfo

/**
 * EngineProxy is a static proxy for all IBlackBoxEngine AIDL calls.
 * Every method checks connection state, wraps AIDL calls in try-catch for RemoteException,
 * and returns sensible defaults on failure.
 */
object EngineProxy {
    private const val TAG = "EngineProxy"

    @Volatile
    private var mEngine: IBlackBoxEngine? = null

    private val serviceAvailableCallbacks = mutableListOf<() -> Unit>()

    fun init(engine: IBlackBoxEngine) {
        mEngine = engine
        Log.d(TAG, "EngineProxy initialized")

        synchronized(serviceAvailableCallbacks) {
            serviceAvailableCallbacks.forEach { callback ->
                try {
                    callback()
                } catch (e: Exception) {
                    Log.w(TAG, "Service available callback failed: ${e.message}")
                }
            }
            serviceAvailableCallbacks.clear()
        }
    }

    fun disconnect() {
        mEngine = null
        Log.d(TAG, "EngineProxy disconnected")
    }

    fun isConnected(): Boolean = mEngine != null

    // === Core APIs ===

    fun launchApk(packageName: String, userId: Int): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "launchApk: Engine not connected")
            return false
        }
        return try {
            mEngine!!.launchApk(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "launchApk failed for $packageName (user=$userId): ${e.message}")
            false
        }
    }

    fun installPackageAsUser(path: String, userId: Int): InstallResult {
        if (!isConnected()) {
            Log.w(TAG, "installPackageAsUser: Engine not connected")
            return InstallResult().installError("Engine not connected")
        }
        return try {
            mEngine!!.installPackageAsUser(path, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "installPackageAsUser failed for $path (user=$userId): ${e.message}")
            InstallResult().installError("IPC error: ${e.message}")
        }
    }

    fun uninstallPackageAsUser(packageName: String, userId: Int) {
        if (!isConnected()) {
            Log.w(TAG, "uninstallPackageAsUser: Engine not connected")
            return
        }
        try {
            mEngine!!.uninstallPackageAsUser(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "uninstallPackageAsUser failed for $packageName (user=$userId): ${e.message}")
        }
    }

    fun getInstalledApplications(flags: Int, userId: Int): List<ApplicationInfo> {
        if (!isConnected()) {
            Log.w(TAG, "getInstalledApplications: Engine not connected")
            return emptyList()
        }
        return try {
            mEngine!!.getInstalledApplications(flags, userId) ?: emptyList()
        } catch (e: RemoteException) {
            Log.e(TAG, "getInstalledApplications failed (user=$userId): ${e.message}")
            emptyList()
        }
    }

    fun getUsers(): List<BUserInfo> {
        if (!isConnected()) {
            Log.w(TAG, "getUsers: Engine not connected")
            return emptyList()
        }
        return try {
            mEngine!!.getUsers() ?: emptyList()
        } catch (e: RemoteException) {
            Log.e(TAG, "getUsers failed: ${e.message}")
            emptyList()
        }
    }

    fun createUser(userId: Int): BUserInfo? {
        if (!isConnected()) {
            Log.w(TAG, "createUser: Engine not connected")
            return null
        }
        return try {
            mEngine!!.createUser(userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "createUser failed for userId=$userId: ${e.message}")
            null
        }
    }

    fun deleteUser(userId: Int) {
        if (!isConnected()) {
            Log.w(TAG, "deleteUser: Engine not connected")
            return
        }
        try {
            mEngine!!.deleteUser(userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "deleteUser failed for userId=$userId: ${e.message}")
        }
    }

    fun isInstalled(packageName: String, userId: Int): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "isInstalled: Engine not connected")
            return false
        }
        return try {
            mEngine!!.isInstalled(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "isInstalled failed for $packageName (user=$userId): ${e.message}")
            false
        }
    }

    fun clearPackage(packageName: String, userId: Int) {
        if (!isConnected()) {
            Log.w(TAG, "clearPackage: Engine not connected")
            return
        }
        try {
            mEngine!!.clearPackage(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "clearPackage failed for $packageName (user=$userId): ${e.message}")
        }
    }

    fun stopPackage(packageName: String, userId: Int) {
        if (!isConnected()) {
            Log.w(TAG, "stopPackage: Engine not connected")
            return
        }
        try {
            mEngine!!.stopPackage(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "stopPackage failed for $packageName (user=$userId): ${e.message}")
        }
    }

    // === Facades ===

    fun getPackageManager(): IBPackageManagerService? {
        if (!isConnected()) {
            Log.w(TAG, "getPackageManager: Engine not connected")
            return null
        }
        return try {
            mEngine!!.packageManager
        } catch (e: RemoteException) {
            Log.e(TAG, "getPackageManager failed: ${e.message}")
            null
        }
    }

    fun getActivityManager(): IBActivityManagerService? {
        if (!isConnected()) {
            Log.w(TAG, "getActivityManager: Engine not connected")
            return null
        }
        return try {
            mEngine!!.activityManager
        } catch (e: RemoteException) {
            Log.e(TAG, "getActivityManager failed: ${e.message}")
            null
        }
    }

    fun getLocationManager(): IBLocationManagerService? {
        if (!isConnected()) {
            Log.w(TAG, "getLocationManager: Engine not connected")
            return null
        }
        return try {
            mEngine!!.locationManager
        } catch (e: RemoteException) {
            Log.e(TAG, "getLocationManager failed: ${e.message}")
            null
        }
    }

    fun getUserManager(): IBUserManagerService? {
        if (!isConnected()) {
            Log.w(TAG, "getUserManager: Engine not connected")
            return null
        }
        return try {
            mEngine!!.userManager
        } catch (e: RemoteException) {
            Log.e(TAG, "getUserManager failed: ${e.message}")
            null
        }
    }

    // === GMS ===

    fun isSupportGms(): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "isSupportGms: Engine not connected")
            return false
        }
        return try {
            mEngine!!.isSupportGms
        } catch (e: RemoteException) {
            Log.e(TAG, "isSupportGms failed: ${e.message}")
            false
        }
    }

    fun isInstallGms(userId: Int): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "isInstallGms: Engine not connected")
            return false
        }
        return try {
            mEngine!!.isInstallGms(userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "isInstallGms failed for userId=$userId: ${e.message}")
            false
        }
    }

    fun installGms(userId: Int): InstallResult {
        if (!isConnected()) {
            Log.w(TAG, "installGms: Engine not connected")
            return InstallResult().installError("Engine not connected")
        }
        return try {
            mEngine!!.installGms(userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "installGms failed for userId=$userId: ${e.message}")
            InstallResult().installError("IPC error: ${e.message}")
        }
    }

    fun uninstallGms(userId: Int): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "uninstallGms: Engine not connected")
            return false
        }
        return try {
            mEngine!!.uninstallGms(userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "uninstallGms failed for userId=$userId: ${e.message}")
            false
        }
    }

    // === Logging ===

    fun sendLogs(caption: String, async: Boolean) {
        if (!isConnected()) {
            Log.w(TAG, "sendLogs: Engine not connected")
            return
        }
        try {
            mEngine!!.sendLogs(caption, async)
        } catch (e: RemoteException) {
            Log.e(TAG, "sendLogs failed: ${e.message}")
        }
    }

    // === ShopId (Phase 4) ===

    fun getShopInfo(packageName: String, userId: Int): ShopInfo? {
        if (!isConnected()) {
            Log.w(TAG, "getShopInfo: Engine not connected")
            return null
        }
        return try {
            mEngine!!.getShopInfo(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "getShopInfo failed for $packageName (user=$userId): ${e.message}")
            null
        }
    }

    fun triggerShopIdExtract(packageName: String, userId: Int) {
        if (!isConnected()) {
            Log.w(TAG, "triggerShopIdExtract: Engine not connected")
            return
        }
        try {
            mEngine!!.triggerShopIdExtract(packageName, userId)
        } catch (e: RemoteException) {
            Log.e(TAG, "triggerShopIdExtract failed for $packageName (user=$userId): ${e.message}")
        }
    }

    // === Session (Phase 6) ===

    fun registerSession(sessionId: String, expireAt: Long) {
        if (!isConnected()) {
            Log.w(TAG, "registerSession: Engine not connected")
            return
        }
        try {
            mEngine!!.registerSession(sessionId, expireAt)
        } catch (e: RemoteException) {
            Log.e(TAG, "registerSession failed: ${e.message}")
        }
    }

    fun unregisterSession() {
        if (!isConnected()) {
            Log.w(TAG, "unregisterSession: Engine not connected")
            return
        }
        try {
            mEngine!!.unregisterSession()
        } catch (e: RemoteException) {
            Log.e(TAG, "unregisterSession failed: ${e.message}")
        }
    }

    fun isSessionActive(): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "isSessionActive: Engine not connected")
            return false
        }
        return try {
            mEngine!!.isSessionActive
        } catch (e: RemoteException) {
            Log.e(TAG, "isSessionActive failed: ${e.message}")
            false
        }
    }

    // === Callbacks ===

    fun addServiceAvailableCallback(callback: () -> Unit) {
        if (isConnected()) {
            try {
                callback()
            } catch (e: Exception) {
                Log.w(TAG, "Service available callback failed: ${e.message}")
            }
        } else {
            synchronized(serviceAvailableCallbacks) {
                serviceAvailableCallbacks.add(callback)
            }
        }
    }
}
