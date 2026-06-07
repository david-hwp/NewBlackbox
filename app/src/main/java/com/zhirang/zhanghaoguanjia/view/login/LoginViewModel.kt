package com.zhirang.zhanghaoguanjia.view.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.bean.dto.UserDto
import com.zhirang.zhanghaoguanjia.data.BaseRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.data.UserRepository
import com.zhirang.zhanghaoguanjia.network.RetrofitClient

class LoginViewModel : ViewModel() {

    private val userRepository = UserRepository(RetrofitClient.apiService)
    private val tokenManager = TokenManager.getInstance()

    val loginResultLiveData = MutableLiveData<Result<UserDto>>()
    val registerResultLiveData = MutableLiveData<Result<Unit>>()
    val loadingLiveData = MutableLiveData<Boolean>()
    val errorLiveData = MutableLiveData<String>()

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            loadingLiveData.value = true
            val result = userRepository.login(phone, password)
            result.fold(
                onSuccess = { (userDto, token) ->
                    BaseRepository.clearAuthRedirecting()
                    tokenManager.saveToken(token)
                    tokenManager.saveUser(userDto)
                    val latestUser = userRepository.getMe().getOrElse { userDto }
                    tokenManager.saveUser(latestUser)
                    loginResultLiveData.value = Result.success(latestUser)
                },
                onFailure = { e ->
                    errorLiveData.value = e.message ?: "登录失败"
                }
            )
            loadingLiveData.value = false
        }
    }

    fun register(phone: String, password: String, username: String) {
        viewModelScope.launch {
            loadingLiveData.value = true
            val result = userRepository.register(phone, password, username)
            result.fold(
                onSuccess = {
                    registerResultLiveData.value = Result.success(Unit)
                },
                onFailure = { e ->
                    errorLiveData.value = e.message ?: "注册失败"
                }
            )
            loadingLiveData.value = false
        }
    }
}
