package top.niunaijun.blackboxa.view.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import top.niunaijun.blackboxa.bean.dto.EngineVersionDto
import top.niunaijun.blackboxa.data.EngineVersionRepository
import top.niunaijun.blackboxa.engine.EngineInstaller
import top.niunaijun.blackboxa.network.RetrofitClient
import java.io.File

class EngineSwitchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EngineVersionRepository(RetrofitClient.apiService)
    private val httpClient = OkHttpClient()

    private val _versionsLiveData = MutableLiveData<List<EngineVersionDto>>()
    val versionsLiveData: LiveData<List<EngineVersionDto>> = _versionsLiveData

    private val _loadingLiveData = MutableLiveData<Boolean>()
    val loadingLiveData: LiveData<Boolean> = _loadingLiveData

    private val _messageLiveData = MutableLiveData<String>()
    val messageLiveData: LiveData<String> = _messageLiveData

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
                val file = download(version)
                val checksum = version.checksum?.takeIf { it.isNotBlank() }
                if (checksum != null) {
                    val actual = EngineInstaller.computeFileMd5(file)
                    if (!actual.equals(checksum, ignoreCase = true)) {
                        _messageLiveData.postValue("引擎包校验失败")
                        _loadingLiveData.postValue(false)
                        return@launch
                    }
                }
                val result = EngineInstaller.installFromFile(getApplication(), file)
                result.fold(
                    onSuccess = { _messageLiveData.postValue("已开始安装引擎") },
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
