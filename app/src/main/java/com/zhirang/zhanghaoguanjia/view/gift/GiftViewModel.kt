package com.zhirang.zhanghaoguanjia.view.gift

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.bean.dto.GiftResult
import com.zhirang.zhanghaoguanjia.data.ComputeRepository
import com.zhirang.zhanghaoguanjia.network.RetrofitClient

class GiftViewModel : ViewModel() {

    private val computeRepository = ComputeRepository(RetrofitClient.apiService)

    val giftResultLiveData = MutableLiveData<Result<GiftResult>>()
    val loadingLiveData = MutableLiveData<Boolean>()
    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<String>()

    fun gift(toPhone: String, amount: Int, phoneMinutes: Boolean = false) {
        viewModelScope.launch {
            loadingLiveData.value = true
            val result = if (phoneMinutes) {
                computeRepository.giftPhoneMinutes(toPhone, amount)
            } else {
                computeRepository.giftCompute(toPhone, amount)
            }
            result.fold(
                onSuccess = {
                    giftResultLiveData.value = Result.success(it)
                    successLiveData.value = "赠送成功"
                },
                onFailure = { e ->
                    errorLiveData.value = e.message ?: "赠送失败"
                }
            )
            loadingLiveData.value = false
        }
    }
}
