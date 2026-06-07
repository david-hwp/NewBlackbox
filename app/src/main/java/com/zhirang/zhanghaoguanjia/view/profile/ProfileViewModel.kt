package com.zhirang.zhanghaoguanjia.view.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.dto.AppVersionDto
import com.zhirang.zhanghaoguanjia.bean.dto.EngineVersionDto
import com.zhirang.zhanghaoguanjia.bean.dto.UserDto
import com.zhirang.zhanghaoguanjia.update.AppUpdateManager
import com.zhirang.zhanghaoguanjia.data.EngineVersionRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.data.UserRepository
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.engine.EngineUpgradeState
import com.zhirang.zhanghaoguanjia.network.RetrofitClient

class ProfileViewModel : ViewModel() {

    private val userRepository = UserRepository(RetrofitClient.apiService)
    private val engineVersionRepository = EngineVersionRepository(RetrofitClient.apiService)
    private val tokenManager = TokenManager.getInstance()

    private val _userProfileLiveData = MutableLiveData<UserDto?>()
    val userProfileLiveData: LiveData<UserDto?> = _userProfileLiveData

    val updateResultLiveData = MutableLiveData<Result<UserDto>>()
    val passwordResultLiveData = MutableLiveData<Result<Unit>>()
    val errorLiveData = MutableLiveData<String>()
    val hasEngineUpgradeLiveData = MutableLiveData<Boolean>()
    val hasAppUpdateLiveData = MutableLiveData<Boolean>()
    val appUpdateLiveData = MutableLiveData<Result<AppVersionDto?>>()
    val updateCheckLiveData = MutableLiveData<Result<UpdateCheckResult>>()
    val appInstallResultLiveData = MutableLiveData<Result<Unit>>()

    fun loadProfile() {
        _userProfileLiveData.value = tokenManager.getUser()
        viewModelScope.launch {
            val result = userRepository.getMe()
            result.fold(
                onSuccess = { user ->
                    tokenManager.saveUser(user)
                    _userProfileLiveData.value = user
                },
                onFailure = { e ->
                    errorLiveData.value = e.message
                }
            )
        }
    }

    fun updateUsername(username: String) {
        updateProfile(username, null, _userProfileLiveData.value?.avatarUrl)
    }

    fun updateProfile(username: String, avatarUri: Uri?, currentAvatarUrl: String?) {
        viewModelScope.launch {
            val avatarUrlResult = if (avatarUri != null) {
                userRepository.uploadAvatar(App.getContext(), avatarUri)
            } else {
                Result.success(currentAvatarUrl)
            }
            if (avatarUrlResult.isFailure) {
                errorLiveData.value = avatarUrlResult.exceptionOrNull()?.message ?: "头像上传失败"
                return@launch
            }
            val result = userRepository.updateUsername(username, avatarUrlResult.getOrNull())
            result.fold(
                onSuccess = { user ->
                    tokenManager.saveUser(user)
                    _userProfileLiveData.value = user
                    updateResultLiveData.value = Result.success(user)
                },
                onFailure = { e ->
                    errorLiveData.value = e.message
                }
            )
        }
    }

    fun updatePassword(oldPwd: String, newPwd: String, confirmPwd: String) {
        if (newPwd != confirmPwd) {
            errorLiveData.value = "两次输入的新密码不一致"
            return
        }
        if (newPwd.length < 6) {
            errorLiveData.value = "新密码不能少于6位"
            return
        }
        viewModelScope.launch {
            val result = userRepository.updatePassword(oldPwd, newPwd, confirmPwd)
            result.fold(
                onSuccess = {
                    passwordResultLiveData.value = Result.success(Unit)
                },
                onFailure = { e ->
                    errorLiveData.value = e.message
                }
            )
        }
    }

    fun refreshEngineUpgradeState() {
        viewModelScope.launch {
            val context = App.getContext()
            val installed = EngineInstaller.getInstalledEngineVersion(context)
            val builtin = EngineInstaller.getBuiltinEngineVersion(context)
            EngineUpgradeState.clearPendingIfInstalled(context, installed)
            val hasBuiltinUpgrade = installed > 0 && builtin > installed

            val result = engineVersionRepository.getAvailableVersions()
            result.fold(
                onSuccess = { versions ->
                    val latest = versions.maxByOrNull { it.versionCode }
                    val latestVersionCode = latest?.versionCode ?: 0
                    val hasServerUpgrade = latestVersionCode > installed
                    if (hasServerUpgrade) {
                        EngineUpgradeState.markPending(context, latestVersionCode)
                    }
                    hasEngineUpgradeLiveData.value =
                        hasBuiltinUpgrade || hasServerUpgrade || EngineUpgradeState.hasPendingUpgrade(context, installed)
                },
                onFailure = {
                    hasEngineUpgradeLiveData.value =
                        hasBuiltinUpgrade || EngineUpgradeState.hasPendingUpgrade(context, installed)
                }
            )
        }
    }

    fun refreshAppUpdateState() {
        viewModelScope.launch {
            val result = AppUpdateManager.checkForUpdate(App.getContext())
            hasAppUpdateLiveData.value = result.getOrNull() != null
        }
    }

    fun checkAppUpdate() {
        viewModelScope.launch {
            val result = AppUpdateManager.checkForUpdate(App.getContext())
            appUpdateLiveData.value = result
        }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            val appResult = AppUpdateManager.checkForUpdate(App.getContext())
            val engineResult = checkEngineUpdate()
            if (appResult.isFailure && engineResult.isFailure) {
                updateCheckLiveData.value = Result.failure(
                    appResult.exceptionOrNull()
                        ?: engineResult.exceptionOrNull()
                        ?: IllegalStateException("检查更新失败")
                )
                return@launch
            }
            updateCheckLiveData.value = Result.success(
                UpdateCheckResult(
                    appVersion = appResult.getOrNull(),
                    engineVersion = engineResult.getOrNull()
                )
            )
        }
    }

    private suspend fun checkEngineUpdate(): Result<EngineVersionDto?> {
        val installed = EngineInstaller.getInstalledEngineVersion(App.getContext())
        return engineVersionRepository.getAvailableVersions().map { versions ->
            versions
                .filter { it.available && it.versionCode > installed }
                .maxByOrNull { it.versionCode }
        }
    }

    fun downloadAndInstallApp(version: AppVersionDto) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = AppUpdateManager.downloadAndInstall(App.getContext(), version)
            appInstallResultLiveData.postValue(result)
        }
    }

    fun logout() {
        tokenManager.clearToken()
        tokenManager.clearUser()
    }

    data class UpdateCheckResult(
        val appVersion: AppVersionDto?,
        val engineVersion: EngineVersionDto?
    )
}
