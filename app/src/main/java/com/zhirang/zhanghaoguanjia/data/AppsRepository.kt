package com.zhirang.zhanghaoguanjia.data

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import androidx.lifecycle.MutableLiveData
import java.io.File
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.app.AppManager
import com.zhirang.zhanghaoguanjia.bean.AppInfo
import com.zhirang.zhanghaoguanjia.bean.InstalledAppBean
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.engine.EngineProxy
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry
import com.zhirang.zhanghaoguanjia.util.MemoryManager
import com.zhirang.zhanghaoguanjia.util.getString


class AppsRepository {
    val TAG: String = "AppsRepository"
    private var mInstalledList = mutableListOf<AppInfo>()

    private fun safeLoadAppLabel(context: android.content.Context, applicationInfo: ApplicationInfo): String {
        return try {
            applicationInfo.loadLabel(context.packageManager).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load label for ${applicationInfo.packageName}: ${e.message}")
            applicationInfo.packageName
        }
    }

    fun previewInstallList(context: android.content.Context) {
        try {
            synchronized(mInstalledList) {
                var installedApplications: List<ApplicationInfo> =
                    context.packageManager.getInstalledApplications(0)

                Log.d(TAG, "previewInstallList: getInstalledApplications returned ${installedApplications.size} apps")

                // Honor/Huawei/MagicOS restriction: getInstalledApplications may return only self
                // when "Read installed app list" permission is denied. Fallback to queryIntentActivities.
                if (installedApplications.size <= 1) {
                    Log.w(TAG, "previewInstallList: Only ${installedApplications.size} app(s) returned, trying queryIntentActivities fallback")
                    try {
                        val pm = context.packageManager
                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                        val resolveInfos = pm.queryIntentActivities(intent, 0)
                        Log.d(TAG, "previewInstallList: queryIntentActivities returned ${resolveInfos.size} apps")

                        val fallbackApps = mutableListOf<ApplicationInfo>()
                        val seenPackages = mutableSetOf<String>()
                        for (ri in resolveInfos) {
                            val ai = ri.activityInfo?.applicationInfo ?: continue
                            if (seenPackages.add(ai.packageName)) {
                                fallbackApps.add(ai)
                            }
                        }
                        if (fallbackApps.size > installedApplications.size) {
                            Log.d(TAG, "previewInstallList: Using fallback with ${fallbackApps.size} apps")
                            installedApplications = fallbackApps
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "previewInstallList: Fallback queryIntentActivities failed: ${e.message}")
                    }
                }

                val installedList = mutableListOf<AppInfo>()

                var skippedSystem = 0
                var skippedAbi = 0
                var skippedBlackBox = 0
                var skippedNullSource = 0
                var processedCount = 0

                for (installedApplication in installedApplications) {
                    try {
                        if (installedApplication.sourceDir.isNullOrBlank()) {
                            skippedNullSource++
                            Log.w(TAG, "Skipping app with null/blank sourceDir: ${installedApplication.packageName}")
                            continue
                        }

                        val file = File(installedApplication.sourceDir)

                        if ((installedApplication.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                            skippedSystem++
                            continue
                        }

                        // Check ABI support using host PackageManager
                        val packageInfo = try {
                            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                        } catch (e: Exception) {
                            null
                        }
                        if (packageInfo == null) {
                            skippedAbi++
                            continue
                        }

                        val hostPackageName = context.packageName
                        if (installedApplication.packageName == hostPackageName) {
                            skippedBlackBox++
                            Log.d(TAG, "Filtering out BlackBox app: ${installedApplication.packageName}")
                            continue
                        }

                        // Also filter out the Engine APK itself
                        if (installedApplication.packageName == EngineInstaller.ENGINE_PACKAGE) {
                            skippedBlackBox++
                            Log.d(TAG, "Filtering out Engine app: ${installedApplication.packageName}")
                            continue
                        }

                        val isXpModule = false

                        val icon = try {
                            installedApplication.loadIcon(context.packageManager)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load icon for ${installedApplication.packageName}: ${e.message}")
                            null
                        }

                        val info =
                            AppInfo(
                                safeLoadAppLabel(context, installedApplication),
                                icon,
                                installedApplication.packageName,
                                installedApplication.sourceDir,
                                isXpModule,
                                null,
                                null,
                                null
                            )
                        installedList.add(info)
                        processedCount++
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error processing app ${installedApplication.packageName}: ${e.message}"
                        )
                    }
                }
                Log.d(TAG, "previewInstallList: skippedSystem=$skippedSystem, skippedAbi=$skippedAbi, " +
                        "skippedBlackBox=$skippedBlackBox, skippedNullSource=$skippedNullSource, processed=$processedCount")
                this.mInstalledList.clear()
                this.mInstalledList.addAll(installedList)
                Log.d(TAG, "previewInstallList: Final mInstalledList size = ${mInstalledList.size}")
                if (mInstalledList.isEmpty()) {
                    Log.w(TAG, "previewInstallList: mInstalledList is empty! " +
                            "This may be caused by Honor/Huawei/MagicOS 'Read installed app list' permission restriction. " +
                            "Please go to Settings > Apps > BlackBox > Permissions and enable 'Read installed app list'.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in previewInstallList: ${e.message}")
        }
    }

    fun getInstalledAppList(
        userID: Int,
        loadingLiveData: MutableLiveData<Boolean>,
        appsLiveData: MutableLiveData<List<InstalledAppBean>>
    ) {
        try {
            loadingLiveData.postValue(true)
            synchronized(mInstalledList) {
                Log.d(TAG, mInstalledList.joinToString(","))
                val newInstalledList =
                    mInstalledList.map {
                        InstalledAppBean(
                            it.name,
                            it.icon,
                            it.packageName,
                            it.sourceDir,
                            EngineProxy.isInstalled(it.packageName, userID)
                        )
                    }
                appsLiveData.postValue(newInstalledList)
                loadingLiveData.postValue(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getInstalledAppList: ${e.message}")
            loadingLiveData.postValue(false)
            appsLiveData.postValue(emptyList())
        }
    }

    fun getVmInstallList(userId: Int, appsLiveData: MutableLiveData<List<AppInfo>>) {
        try {

            if (MemoryManager.isMemoryCritical()) {
                Log.w(
                    TAG,
                    "Memory critical (${MemoryManager.getMemoryUsagePercentage()}%), forcing garbage collection"
                )
                MemoryManager.forceGarbageCollectionIfNeeded()
            }

            val users = EngineProxy.getUsers()
            Log.d(TAG, "getVmInstallList: userId=$userId, total users=${users.size}")
            users.forEach { user -> Log.d(TAG, "User: id=${user.id}, name=${user.name}") }

            val sortListData = AppManager.mRemarkSharedPreferences.getString("AppList$userId", "")
            val sortList = sortListData?.split(",")

            var applicationList: List<ApplicationInfo>? = null
            var retryCount = 0
            val maxRetries = 3

            while ((applicationList == null || applicationList.isEmpty()) && retryCount < maxRetries) {
                try {
                    applicationList = EngineProxy.getInstalledApplications(0, userId)
                    if (applicationList == null || applicationList.isEmpty()) {
                        Log.w(
                            TAG,
                            "getVmInstallList: Attempt ${retryCount + 1} returned null/empty, retrying..."
                        )
                        retryCount++
                        if (retryCount < maxRetries) {
                            Thread.sleep(300)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "getVmInstallList: Error getting applications on attempt ${retryCount + 1}: ${e.message}"
                    )
                    retryCount++
                    if (retryCount < maxRetries) {
                        Thread.sleep(500)
                    }
                }
            }


            if (applicationList == null || applicationList.isEmpty()) {
                Log.w(
                    TAG,
                    "getVmInstallList: applicationList is null/empty for userId=$userId after $maxRetries attempts"
                )
                // Do NOT clear the list here — the list may have disappeared due to a transient
                // Binder service issue. Preserve whatever was previously shown.
                val currentValue = appsLiveData.value
                if (currentValue.isNullOrEmpty()) {
                    appsLiveData.postValue(emptyList())
                } else {
                    Log.d(TAG, "getVmInstallList: Preserving existing list of ${currentValue.size} apps")
                    appsLiveData.postValue(currentValue)
                }
                return
            }


            Log.d(
                TAG,
                "getVmInstallList: userId=$userId, applicationList.size=${applicationList.size}"
            )
            if (applicationList.isNotEmpty()) {
                Log.d(TAG, "First app: ${applicationList.first().packageName}")
            } else {
                Log.w(TAG, "getVmInstallList: No applications found for userId=$userId")
            }

            val appInfoList = mutableListOf<AppInfo>()

            val sortedApplicationList =
                if (!sortList.isNullOrEmpty()) {
                    try {
                        applicationList.sortedWith(AppsSortComparator(sortList))
                    } catch (e: Exception) {
                        Log.e(TAG, "getVmInstallList: Error sorting applications: ${e.message}")
                        applicationList
                    }
                } else {
                    applicationList
                }

            sortedApplicationList.forEachIndexed { index, applicationInfo ->
                try {
                    if (index > 0 && index % 25 == 0) {
                        if (MemoryManager.isMemoryCritical()) {
                            Log.w(TAG, "Memory critical during processing, forcing GC")
                            MemoryManager.forceGarbageCollectionIfNeeded()
                        }
                    }

                    if (applicationInfo == null) {
                        Log.w(
                            TAG,
                            "getVmInstallList: Skipping null applicationInfo at index $index"
                        )
                        return@forEachIndexed
                    }

                    if (applicationInfo.packageName.isNullOrBlank()) {
                        Log.w(
                            TAG,
                            "getVmInstallList: Skipping app with null/blank package name at index $index"
                        )
                        return@forEachIndexed
                    }

                    val shopInfo = try {
                        EngineProxy.getShopInfo(applicationInfo.packageName, userId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get shop info for ${applicationInfo.packageName}: ${e.message}")
                        null
                    }

                    val platformItem = PlatformRegistry.preferredPlatformForPackage(applicationInfo.packageName)
                    val platformAvailable = platformItem?.available ?: true
                    val icon = try {
                        applicationInfo.loadIcon(App.getContext().packageManager)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load icon for ${applicationInfo.packageName}: ${e.message}")
                        null
                    }

                    val info =
                        AppInfo(
                            applicationInfo.loadLabel(App.getContext().packageManager).toString(),
                            icon,
                            applicationInfo.packageName,
                            applicationInfo.sourceDir ?: "",
                            false,
                            shopInfo?.shopId,
                            shopInfo?.shopName,
                            shopInfo?.platform,
                            platformAvailable,
                            platformItem?.iconKey,
                            platformItem?.packageName
                        )

                    appInfoList.add(info)

                    if (index > 0 && index % 50 == 0) {
                        Log.d(
                            TAG,
                            "getVmInstallList: Processed $index/${sortedApplicationList.size} apps - ${MemoryManager.getMemoryInfo()}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "getVmInstallList: Error processing app at index $index (${applicationInfo?.packageName}): ${e.message}"
                    )
                }
            }

            Log.d(
                TAG,
                "getVmInstallList: processed ${appInfoList.size} apps - ${MemoryManager.getMemoryInfo()}"
            )

            if (appInfoList.isEmpty()) {
                Log.d(
                    TAG,
                    "getVmInstallList: No virtual apps found for userId=$userId, showing empty list (correct for new users)"
                )
            } else {
                Log.d(
                    TAG,
                    "getVmInstallList: Showing ${appInfoList.size} virtual apps for userId=$userId"
                )
            }

            try {
                appsLiveData.postValue(appInfoList)
            } catch (e: Exception) {
                Log.e(TAG, "getVmInstallList: Error posting to LiveData: ${e.message}")

                try {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            appsLiveData.postValue(appInfoList)
                        } catch (e2: Exception) {
                            Log.e(
                                TAG,
                                "getVmInstallList: Fallback posting also failed: ${e2.message}"
                            )
                        }
                    }
                } catch (e3: Exception) {
                    Log.e(
                        TAG,
                        "getVmInstallList: Could not schedule fallback posting: ${e3.message}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getVmInstallList: ${e.message}", e)
            // Do NOT clear the list on unexpected errors.
            // Preserve the current list to avoid the "apps disappearing" issue.
            val currentValue = appsLiveData.value
            if (currentValue.isNullOrEmpty()) {
                appsLiveData.postValue(emptyList())
            } else {
                Log.d(TAG, "getVmInstallList: Preserving existing list of ${currentValue.size} apps after error")
                appsLiveData.postValue(currentValue)
            }
        }
    }

    fun installApk(source: String, userId: Int, resultLiveData: MutableLiveData<String>) {
        try {
            if (source.contains("blackbox") ||
                source.contains("niunaijun") ||
                source.contains("vspace") ||
                source.contains("virtual")
            ) {
                try {
                    val hostPackageName = App.getContext().packageName

                    if (!URLUtil.isValidUrl(source)) {
                        val file = File(source)
                        if (file.exists()) {
                            val packageInfo =
                                App.getContext().packageManager.getPackageArchiveInfo(source, 0)
                            if (packageInfo != null && packageInfo.packageName == hostPackageName) {
                                resultLiveData.postValue(
                                    "Cannot install BlackBox app from within BlackBox. This would create infinite recursion and is not allowed for security reasons."
                                )
                                return
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify if this is BlackBox app: ${e.message}")
                }
            }

            val installResult =
                if (URLUtil.isValidUrl(source)) {
                    // For URL installs, download first then install - simplified for now
                    EngineProxy.installPackageAsUser(source, userId)
                } else {
                    EngineProxy.installPackageAsUser(source, userId)
                }

            if (installResult.success) {
                updateAppSortList(userId, installResult.packageName, true)
                resultLiveData.postValue(getString(R.string.install_success))
            } else {
                resultLiveData.postValue(getString(R.string.install_fail, installResult.msg))
            }
            scanUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}")
            resultLiveData.postValue("Installation failed: ${e.message}")
        }
    }

    fun unInstall(packageName: String, userID: Int, resultLiveData: MutableLiveData<String>) {
        try {
            EngineProxy.uninstallPackageAsUser(packageName, userID)
            updateAppSortList(userID, packageName, false)
            scanUser()
            resultLiveData.postValue(getString(R.string.uninstall_success))
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling APK: ${e.message}")
            resultLiveData.postValue("Uninstallation failed: ${e.message}")
        }
    }

    fun launchApk(packageName: String, userId: Int, launchLiveData: MutableLiveData<Boolean>) {
        try {
            val intent = EngineProxy.getLaunchIntent(packageName, userId)
            if (intent != null) {
                App.getContext().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                launchLiveData.postValue(true)
            } else {
                Log.w(TAG, "launchApk: getLaunchIntent returned null")
                launchLiveData.postValue(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching APK: ${e.message}")
            launchLiveData.postValue(false)
        }
    }

    fun clearApkData(packageName: String, userID: Int, resultLiveData: MutableLiveData<String>) {
        try {
            EngineProxy.clearPackage(packageName, userID)
            resultLiveData.postValue(getString(R.string.clear_success))
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing APK data: ${e.message}")
            resultLiveData.postValue("Clear failed: ${e.message}")
        }
    }

    private fun scanUser() {
        try {
            val userList = EngineProxy.getUsers()

            if (userList.isEmpty()) {
                return
            }

            val id = userList.last().id

            if (EngineProxy.getInstalledApplications(0, id).isEmpty()) {
                EngineProxy.deleteUser(id)
                AppManager.mRemarkSharedPreferences.edit().apply {
                    remove("Remark$id")
                    remove("AppList$id")
                    apply()
                }
                scanUser()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanUser: ${e.message}")
        }
    }

    private fun updateAppSortList(userID: Int, pkg: String, isAdd: Boolean) {
        try {
            val savedSortList = AppManager.mRemarkSharedPreferences.getString("AppList$userID", "")

            val sortList = linkedSetOf<String>()
            if (savedSortList != null) {
                sortList.addAll(savedSortList.split(","))
            }

            if (isAdd) {
                sortList.add(pkg)
            } else {
                sortList.remove(pkg)
            }

            AppManager.mRemarkSharedPreferences.edit().apply {
                putString("AppList$userID", sortList.joinToString(","))
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating app sort list: ${e.message}")
        }
    }

    fun updateApkOrder(userID: Int, dataList: List<AppInfo>) {
        try {
            AppManager.mRemarkSharedPreferences.edit().apply {
                putString("AppList$userID", dataList.joinToString(",") { it.packageName })
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating APK order: ${e.message}")
        }
    }
}
