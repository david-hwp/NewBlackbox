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

    fun isConnected(): Boolean {
        val binder = mEngine?.asBinder() ?: return false
        if (!binder.isBinderAlive) {
            Log.w(TAG, "Engine binder is no longer alive")
            disconnect()
            return false
        }
        return true
    }

    private fun markRemoteFailure(action: String, e: RemoteException) {
        Log.e(TAG, "$action remote call failed: ${e.message}", e)
        disconnect()
    }

    // === Core APIs ===

    fun getLaunchIntent(packageName: String, userId: Int): android.content.Intent? {
        if (!isConnected()) {
            Log.w(TAG, "getLaunchIntent: Engine not connected")
            return null
        }
        return try {
            mEngine!!.getLaunchIntent(packageName, userId)
        } catch (e: RemoteException) {
            markRemoteFailure("getLaunchIntent($packageName, user=$userId)", e)
            null
        }
    }

    fun launchApk(packageName: String, userId: Int): Boolean {
        if (!isConnected()) {
            Log.w(TAG, "launchApk: Engine not connected")
            return false
        }
        return try {
            mEngine!!.launchApk(packageName, userId)
        } catch (e: RemoteException) {
            markRemoteFailure("launchApk($packageName, user=$userId)", e)
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
            markRemoteFailure("installPackageAsUser($path, user=$userId)", e)
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
            markRemoteFailure("uninstallPackageAsUser($packageName, user=$userId)", e)
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
            markRemoteFailure("getInstalledApplications(user=$userId)", e)
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
            markRemoteFailure("getUsers", e)
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
            markRemoteFailure("createUser(userId=$userId)", e)
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
            markRemoteFailure("deleteUser(userId=$userId)", e)
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
            markRemoteFailure("isInstalled($packageName, user=$userId)", e)
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
            markRemoteFailure("clearPackage($packageName, user=$userId)", e)
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
            markRemoteFailure("stopPackage($packageName, user=$userId)", e)
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
            markRemoteFailure("getPackageManager", e)
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
            markRemoteFailure("getActivityManager", e)
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
            markRemoteFailure("getLocationManager", e)
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
            markRemoteFailure("getUserManager", e)
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
            markRemoteFailure("isSupportGms", e)
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
            markRemoteFailure("isInstallGms(userId=$userId)", e)
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
            markRemoteFailure("installGms(userId=$userId)", e)
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
            markRemoteFailure("uninstallGms(userId=$userId)", e)
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
            markRemoteFailure("sendLogs", e)
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
            markRemoteFailure("getShopInfo($packageName, user=$userId)", e)
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
            markRemoteFailure("triggerShopIdExtract($packageName, user=$userId)", e)
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
            markRemoteFailure("registerSession", e)
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
            markRemoteFailure("unregisterSession", e)
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
            markRemoteFailure("isSessionActive", e)
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
