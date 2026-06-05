package com.zhirang.zhanghaoguanjia.view.login

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.bean.dto.UserDto
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.data.UserRepository
import com.zhirang.zhanghaoguanjia.network.RetrofitClient

class LoginViewModel : ViewModel() {

    private val userRepository = UserRepository(RetrofitClient.apiService)
    private val tokenManager = TokenManager.getInstance()

    val loginResultLiveData = MutableLiveData<Result<UserDto>>()
    val registerResultLiveData = MutableLiveData<Result<UserDto>>()
    val loadingLiveData = MutableLiveData<Boolean>()
    val errorLiveData = MutableLiveData<String>()

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            loadingLiveData.value = true
            val result = userRepository.login(phone, password)
            result.fold(
                onSuccess = { (userDto, token) ->
                    tokenManager.saveToken(token)
                    tokenManager.saveUser(userDto)
                    loginResultLiveData.value = Result.success(userDto)
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
                onSuccess = { userDto ->
                    userDto.token?.let { tokenManager.saveToken(it) }
                    tokenManager.saveUser(userDto)
                    registerResultLiveData.value = Result.success(userDto)
                },
                onFailure = { e ->
                    errorLiveData.value = e.message ?: "注册失败"
                }
            )
            loadingLiveData.value = false
        }
    }
}
