package top.niunaijun.blackboxa.view.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.niunaijun.blackboxa.bean.Platform
import top.niunaijun.blackboxa.bean.Shop
import top.niunaijun.blackboxa.bean.dto.ShopReportRequest
import top.niunaijun.blackboxa.data.ShopRepository
import top.niunaijun.blackboxa.data.TokenManager
import top.niunaijun.blackboxa.network.RetrofitClient

class HomeViewModel : ViewModel() {

    private val _shopsLiveData = MutableLiveData<List<Shop>>()
    val shopsLiveData: LiveData<List<Shop>> = _shopsLiveData

    private val _selectedPlatformLiveData = MutableLiveData<Platform>()
    val selectedPlatformLiveData: LiveData<Platform> = _selectedPlatformLiveData

    private val _searchQueryLiveData = MutableLiveData<String>("")
    val searchQueryLiveData: LiveData<String> = _searchQueryLiveData

    private val _computeBalanceLiveData = MutableLiveData<Int>()
    val computeBalanceLiveData: LiveData<Int> = _computeBalanceLiveData

    private val shopRepository = ShopRepository(RetrofitClient.apiService)
    private val tokenManager = TokenManager.getInstance()

    private var allShops: List<Shop> = emptyList()

    init {
        loadShops()
        _selectedPlatformLiveData.value = Platform.MEITUAN
        _computeBalanceLiveData.value = tokenManager.getUser()?.computeBalance ?: 0
    }

    fun loadShops() {
        allShops = createMockShops()
        _shopsLiveData.value = allShops
    }

    fun selectPlatform(platform: Platform) {
        _selectedPlatformLiveData.value = platform
    }

    fun search(query: String) {
        _searchQueryLiveData.value = query
    }

    fun getFilteredShops(): List<Shop> {
        val platform = _selectedPlatformLiveData.value
        val query = _searchQueryLiveData.value ?: ""

        return allShops.filter { shop ->
            val matchPlatform = platform == null || shop.platform == platform
            val matchQuery = query.isEmpty() ||
                    shop.shopName.contains(query, ignoreCase = true) ||
                    shop.shopId.contains(query, ignoreCase = true)
            matchPlatform && matchQuery
        }
    }

    fun updateComputeBalance(balance: Int) {
        _computeBalanceLiveData.value = balance
    }

    fun reportShop(shop: Shop) {
        viewModelScope.launch {
            val request = ShopReportRequest(
                shopName = shop.shopName,
                shopId = shop.shopId,
                platform = shop.platform.id,
                platformName = shop.platform.displayName,
                packageName = shop.packageName ?: "",
                remainingDays = shop.remainingDays,
                autoRenew = shop.autoRenew
            )
            shopRepository.reportShop(request)
        }
    }

    private fun createMockShops(): List<Shop> = listOf(
        Shop(
            id = 1,
            shopName = "美团外卖·xx路店",
            shopId = "M123456",
            platform = Platform.MEITUAN,
            remainingDays = 7,
            autoRenew = true,
            packageName = "com.sankuai.meituan.merchant"
        ),
        Shop(
            id = 2,
            shopName = "美团外卖·yy广场店",
            shopId = "M789012",
            platform = Platform.MEITUAN,
            remainingDays = 15,
            autoRenew = false
        ),
        Shop(
            id = 3,
            shopName = "淘宝闪购·yy店",
            shopId = "T789012",
            platform = Platform.TAOBAO,
            remainingDays = 15,
            autoRenew = false
        ),
        Shop(
            id = 4,
            shopName = "京东秒送·zz店",
            shopId = "J345678",
            platform = Platform.JD,
            remainingDays = 3,
            autoRenew = true,
            packageName = "com.jd.mrd.jingming"
        ),
        Shop(
            id = 5,
            shopName = "快手团购·aa店",
            shopId = "K901234",
            platform = Platform.KUAISHOU,
            remainingDays = 30,
            autoRenew = true
        ),
        Shop(
            id = 6,
            shopName = "小红书·bb店",
            shopId = "X567890",
            platform = Platform.XIAOHONGSHU,
            remainingDays = 0,
            autoRenew = false
        ),
        Shop(
            id = 7,
            shopName = "阿里本地·cc店",
            shopId = "A123789",
            platform = Platform.ALI,
            remainingDays = 12,
            autoRenew = true
        ),
        Shop(
            id = 8,
            shopName = "淘宝闪购·dd店",
            shopId = "T456123",
            platform = Platform.TAOBAO,
            remainingDays = 8,
            autoRenew = false
        )
    )
}
