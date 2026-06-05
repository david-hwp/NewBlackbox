package com.zhirang.zhanghaoguanjia.view.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.dto.UserDto
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.data.UserRepository
import com.zhirang.zhanghaoguanjia.network.RetrofitClient

class ProfileViewModel : ViewModel() {

    private val userRepository = UserRepository(RetrofitClient.apiService)
    private val tokenManager = TokenManager.getInstance()

    private val _userProfileLiveData = MutableLiveData<UserDto?>()
    val userProfileLiveData: LiveData<UserDto?> = _userProfileLiveData

    val updateResultLiveData = MutableLiveData<Result<UserDto>>()
    val passwordResultLiveData = MutableLiveData<Result<Unit>>()
    val errorLiveData = MutableLiveData<String>()

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

    fun logout() {
        tokenManager.clearToken()
        tokenManager.clearUser()
    }
}
