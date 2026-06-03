package top.niunaijun.blackboxa.data

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.bean.FakeLocationBean
import top.niunaijun.blackboxa.engine.EngineProxy


class FakeLocationRepository {
    val TAG: String = "FakeLocationRepository"

    fun setPattern(userId: Int, pkg: String, pattern: Int) {
        try {
            EngineProxy.getLocationManager()?.setPattern(userId, pkg, pattern)
        } catch (e: Exception) {
            Log.e(TAG, "setPattern failed: ${e.message}")
        }
    }

    private fun getPattern(userId: Int, pkg: String): Int {
        return try {
            EngineProxy.getLocationManager()?.getPattern(userId, pkg) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "getPattern failed: ${e.message}")
            0
        }
    }

    private fun getLocation(userId: Int, pkg: String): BLocation? {
        return try {
            EngineProxy.getLocationManager()?.getLocation(userId, pkg)
        } catch (e: Exception) {
            Log.e(TAG, "getLocation failed: ${e.message}")
            null
        }
    }

    fun setLocation(userId: Int, pkg: String, location: BLocation) {
        try {
            EngineProxy.getLocationManager()?.setLocation(userId, pkg, location)
        } catch (e: Exception) {
            Log.e(TAG, "setLocation failed: ${e.message}")
        }
    }

    fun getInstalledAppList(
        userID: Int,
        appsFakeLiveData: MutableLiveData<List<FakeLocationBean>>
    ) {
        try {
            val installedList = mutableListOf<FakeLocationBean>()
            val installedApplications: List<ApplicationInfo> =
                EngineProxy.getInstalledApplications(0, userID)

            val pm = App.getContext().packageManager

            for (installedApplication in installedApplications) {
                val icon = try {
                    installedApplication.loadIcon(pm)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load icon for ${installedApplication.packageName}: ${e.message}")
                    null
                }
                val info = FakeLocationBean(
                    userID,
                    installedApplication.loadLabel(pm).toString(),
                    icon,
                    installedApplication.packageName,
                    getPattern(userID, installedApplication.packageName),
                    getLocation(userID, installedApplication.packageName)
                )
                installedList.add(info)
            }

            Log.d(TAG, installedList.joinToString(","))
            appsFakeLiveData.postValue(installedList)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getInstalledAppList: ${e.message}")
            appsFakeLiveData.postValue(emptyList())
        }
    }
}
