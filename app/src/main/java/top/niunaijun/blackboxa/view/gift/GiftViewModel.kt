package top.niunaijun.blackboxa.view.gift

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.niunaijun.blackboxa.bean.dto.GiftResult
import top.niunaijun.blackboxa.data.ComputeRepository
import top.niunaijun.blackboxa.network.RetrofitClient

class GiftViewModel : ViewModel() {

    private val computeRepository = ComputeRepository(RetrofitClient.apiService)

    val giftResultLiveData = MutableLiveData<Result<GiftResult>>()
    val loadingLiveData = MutableLiveData<Boolean>()
    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<String>()

    fun gift(toPhone: String, amount: Int) {
        viewModelScope.launch {
            loadingLiveData.value = true
            val result = computeRepository.giftCompute(toPhone, amount)
            result.fold(
                onSuccess = {
                    successLiveData.value = "赠送成功"
                    giftResultLiveData.value = Result.success(it)
                },
                onFailure = { e ->
                    errorLiveData.value = e.message ?: "赠送失败"
                }
            )
            loadingLiveData.value = false
        }
    }
}
