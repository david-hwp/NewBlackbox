package com.zhirang.zhanghaoguanjia.view.reclaim

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhirang.zhanghaoguanjia.bean.dto.ComputeReclaimResult
import com.zhirang.zhanghaoguanjia.data.ComputeRepository
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import kotlinx.coroutines.launch

class ComputeReclaimViewModel : ViewModel() {

    private val computeRepository = ComputeRepository(RetrofitClient.apiService)

    val queryResultLiveData = MutableLiveData<Result<ComputeReclaimResult>>()
    val reclaimResultLiveData = MutableLiveData<Result<ComputeReclaimResult>>()
    val loadingLiveData = MutableLiveData<Boolean>()
    val errorLiveData = MutableLiveData<String>()

    fun query(toPhone: String) {
        viewModelScope.launch {
            loadingLiveData.value = true
            val result = computeRepository.queryReclaimableCompute(toPhone)
            queryResultLiveData.value = result
            result.exceptionOrNull()?.let { errorLiveData.value = it.message ?: "查询失败" }
            loadingLiveData.value = false
        }
    }

    fun reclaim(toPhone: String, giftLogId: Long?, amount: Int) {
        viewModelScope.launch {
            loadingLiveData.value = true
            val result = computeRepository.reclaimCompute(toPhone, giftLogId, amount)
            reclaimResultLiveData.value = result
            result.exceptionOrNull()?.let { errorLiveData.value = it.message ?: "取回失败" }
            loadingLiveData.value = false
        }
    }
}
