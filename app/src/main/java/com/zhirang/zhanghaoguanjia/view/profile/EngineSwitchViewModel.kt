package com.zhirang.zhanghaoguanjia.view.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import com.zhirang.zhanghaoguanjia.bean.dto.EngineVersionDto
import com.zhirang.zhanghaoguanjia.data.EngineVersionRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.engine.EngineUpgradeState
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import com.zhirang.zhanghaoguanjia.update.PackageIntegrityVerifier
import java.io.File
import android.util.Log

class EngineSwitchViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        private const val TAG = "EngineSwitchViewModel"
    }

    private val repository = EngineVersionRepository(RetrofitClient.apiService)
    private val httpClient = OkHttpClient()

    private val _versionsLiveData = MutableLiveData<List<EngineVersionDto>>()
    val versionsLiveData: LiveData<List<EngineVersionDto>> = _versionsLiveData

    private val _loadingLiveData = MutableLiveData<Boolean>()
    val loadingLiveData: LiveData<Boolean> = _loadingLiveData

    private val _messageLiveData = MutableLiveData<String>()
    val messageLiveData: LiveData<String> = _messageLiveData

    private val _installStartedLiveData = MutableLiveData<EngineVersionDto>()
    val installStartedLiveData: LiveData<EngineVersionDto> = _installStartedLiveData

    fun loadVersions() {
        viewModelScope.launch {
            _loadingLiveData.value = true
            val result = repository.getAvailableVersions()
            result.fold(
                onSuccess = { _versionsLiveData.value = it },
                onFailure = { _messageLiveData.value = it.message ?: "获取引擎版本失败" }
            )
            _loadingLiveData.value = false
        }
    }

    fun downloadAndInstall(version: EngineVersionDto) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingLiveData.postValue(true)
            try {
                if (!TokenManager.getInstance().isLoggedIn()) {
                    _messageLiveData.postValue("请先登录后再升级")
                    _loadingLiveData.postValue(false)
                    return@launch
                }
                val file = download(version)
                val validation = EngineInstaller.validateInstallCandidate(getApplication(), file)
                if (validation.isFailure) {
                    _messageLiveData.postValue(validation.exceptionOrNull()?.message ?: "引擎包不可安装")
                    _loadingLiveData.postValue(false)
                    return@launch
                }
                if (validation.getOrThrow().versionCode != version.versionCode) {
                    _messageLiveData.postValue("引擎包版本号与发布记录不一致")
                    _loadingLiveData.postValue(false)
                    return@launch
                }
                if (!PackageIntegrityVerifier.verifyBeforeUpgrade(
                        PackageIntegrityVerifier.PackageType.ENGINE,
                        file,
                        version.versionCode,
                        version.checksum,
                        validation.getOrThrow().packageName
                    )
                ) {
                    _messageLiveData.postValue(PackageIntegrityVerifier.VERIFY_FAILED_MESSAGE)
                    _loadingLiveData.postValue(false)
                    return@launch
                }
                val result = EngineInstaller.installFromFile(getApplication(), file)
                result.fold(
                    onSuccess = {
                        EngineUpgradeState.markPending(getApplication(), version.versionCode)
                        _installStartedLiveData.postValue(version)
                        _messageLiveData.postValue("已开始安装引擎，请在系统弹窗中确认")
                    },
                    onFailure = { _messageLiveData.postValue(it.message ?: "安装失败") }
                )
            } catch (e: Exception) {
                _messageLiveData.postValue(e.message ?: "下载失败")
            } finally {
                _loadingLiveData.postValue(false)
            }
        }
    }

    private fun download(version: EngineVersionDto): File {
        val request = Request.Builder().url(RetrofitClient.resolveUrl(version.apkUrl)).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("下载失败: ${response.code}")
        }
        val body = response.body ?: throw IllegalStateException("下载内容为空")
        val dir = File(getApplication<Application>().cacheDir, "engine-downloads").apply { mkdirs() }
        val file = File(dir, "engine-${version.versionCode}.apk")
        body.byteStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}
