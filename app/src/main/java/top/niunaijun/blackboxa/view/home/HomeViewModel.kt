package top.niunaijun.blackboxa.view.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.niunaijun.blackboxa.bean.Platform
import top.niunaijun.blackboxa.bean.Shop
import top.niunaijun.blackboxa.bean.dto.AnnouncementDto
import top.niunaijun.blackboxa.bean.dto.PlatformItemDto
import top.niunaijun.blackboxa.bean.dto.ShopDto
import top.niunaijun.blackboxa.bean.dto.ShopReportRequest
import top.niunaijun.blackboxa.data.AnnouncementRepository
import top.niunaijun.blackboxa.data.PlatformRepository
import top.niunaijun.blackboxa.data.ShopRepository
import top.niunaijun.blackboxa.data.TokenManager
import top.niunaijun.blackboxa.network.RetrofitClient

class HomeViewModel : ViewModel() {

    private val _shopsLiveData = MutableLiveData<List<Shop>>()
    val shopsLiveData: LiveData<List<Shop>> = _shopsLiveData

    private val _selectedPlatformLiveData = MutableLiveData<Platform>()
    val selectedPlatformLiveData: LiveData<Platform> = _selectedPlatformLiveData

    private val _platformsLiveData = MutableLiveData<List<PlatformItemDto>>()
    val platformsLiveData: LiveData<List<PlatformItemDto>> = _platformsLiveData

    private val _searchQueryLiveData = MutableLiveData<String>("")
    val searchQueryLiveData: LiveData<String> = _searchQueryLiveData

    private val _computeBalanceLiveData = MutableLiveData<Int>()
    val computeBalanceLiveData: LiveData<Int> = _computeBalanceLiveData

    private val _phoneNumberLiveData = MutableLiveData<String>()
    val phoneNumberLiveData: LiveData<String> = _phoneNumberLiveData

    private val _displayUsernameLiveData = MutableLiveData<String>()
    val displayUsernameLiveData: LiveData<String> = _displayUsernameLiveData

    private val _latestAnnouncementLiveData = MutableLiveData<AnnouncementDto?>()
    val latestAnnouncementLiveData: LiveData<AnnouncementDto?> = _latestAnnouncementLiveData

    private val _platformShopCounts = MutableLiveData<Map<Platform, Int>>()
    val platformShopCounts: LiveData<Map<Platform, Int>> = _platformShopCounts

    private val _loadErrorLiveData = MutableLiveData<String?>()
    val loadErrorLiveData: LiveData<String?> = _loadErrorLiveData

    private val _operationMessageLiveData = MutableLiveData<String?>()
    val operationMessageLiveData: LiveData<String?> = _operationMessageLiveData

    private val shopRepository = ShopRepository(RetrofitClient.apiService)
    private val platformRepository = PlatformRepository(RetrofitClient.apiService)
    private val announcementRepository = AnnouncementRepository(RetrofitClient.apiService)
    private val tokenManager = TokenManager.getInstance()

    private var allShops: List<Shop> = emptyList()

    init {
        refreshUserInfo()
        loadPlatforms()
        loadShops()
    }

    fun loadLatestAnnouncement() {
        viewModelScope.launch {
            val result = announcementRepository.getPublishedAnnouncements()
            result.fold(
                onSuccess = { announcements ->
                    _latestAnnouncementLiveData.value = announcements.firstOrNull()
                },
                onFailure = {
                    _latestAnnouncementLiveData.value = null
                }
            )
        }
    }

    fun refreshUserInfo() {
        val user = tokenManager.getUser()
        _computeBalanceLiveData.value = user?.computeBalance ?: 0
        _phoneNumberLiveData.value = maskPhoneNumber(user?.phone ?: "")
        _displayUsernameLiveData.value = getDisplayUsername(user?.username, user?.phone)
    }

    fun loadShops() {
        viewModelScope.launch {
            val result = shopRepository.getMyShopsFromApi()
            result.fold(
                onSuccess = { shopDtos ->
                    allShops = shopDtos.map { it.toShop() }
                    _shopsLiveData.value = allShops
                    _platformShopCounts.value = allShops.groupingBy { it.platform }.eachCount()
                    _loadErrorLiveData.value = null
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                    allShops = emptyList()
                    _shopsLiveData.value = allShops
                    _platformShopCounts.value = emptyMap()
                }
            )
        }
    }

    fun loadPlatforms() {
        viewModelScope.launch {
            val result = platformRepository.getPlatforms()
            result.fold(
                onSuccess = { platformDtos ->
                    val platforms = platformDtos.map { it.toPlatformItem() }
                    _platformsLiveData.value = platforms
                    val selected = _selectedPlatformLiveData.value
                    if (selected == null || platforms.none { it.platform == selected && it.available }) {
                        platforms.firstOrNull { it.available }?.let {
                            _selectedPlatformLiveData.value = it.platform
                        }
                    }
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                    _platformsLiveData.value = emptyList()
                }
            )
        }
    }

    private fun maskPhoneNumber(phone: String): String {
        return if (phone.length == 11) {
            "${phone.take(3)}****${phone.takeLast(4)}"
        } else {
            phone
        }
    }

    private fun getDisplayUsername(username: String?, phone: String?): String {
        return username?.takeIf { it.isNotBlank() }
            ?: phone?.takeIf { it.isNotBlank() }
            ?: "我的账号"
    }

    fun selectPlatform(platform: Platform) {
        _selectedPlatformLiveData.value = platform
    }

    fun getSelectedPlatformItem(): PlatformItemDto? {
        val selected = _selectedPlatformLiveData.value
        return _platformsLiveData.value?.firstOrNull { it.platform == selected }
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

    fun getCurrentUserId(): Long {
        return tokenManager.getUser()?.id ?: 0L
    }

    fun reportShop(shop: Shop, showMessage: Boolean = true, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val request = ShopReportRequest(
                shopName = shop.shopName,
                shopId = shop.shopId,
                platform = shop.platform.id,
                platformName = shop.platform.displayName,
                packageName = shop.packageName ?: "",
                cloneInstanceId = shop.cloneInstanceId,
                remainingDays = shop.remainingDays,
                autoRenew = shop.autoRenew
            )
            val result = shopRepository.reportShop(request)
            result.fold(
                onSuccess = {
                    _computeBalanceLiveData.value = it.balance
                    tokenManager.getUser()?.let { user ->
                        tokenManager.saveUser(
                            user.copy(
                                computeBalance = it.balance,
                                shopCount = it.shopCount ?: user.shopCount,
                                platformCount = it.platformCount ?: user.platformCount
                            )
                        )
                    }
                    if (showMessage) {
                        _operationMessageLiveData.value = if (it.isNew) "店铺已添加" else "店铺已更新"
                    }
                    loadShops()
                    onComplete?.invoke()
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                }
            )
        }
    }

    fun createPendingShop(platformItem: PlatformItemDto) {
        viewModelScope.launch {
            if (allShops.any { it.platform == platformItem.platform && it.isNew }) {
                _operationMessageLiveData.value = "您已添加新店铺但未成功登录，请先完成登录后再添加"
                return@launch
            }
            val user = tokenManager.getUser()
            val request = ShopDto(
                id = 0,
                shopName = "User[${user?.id ?: 0}]-未知",
                shopId = "${ShopDto.TEMP_SHOP_ID_PREFIX}${System.currentTimeMillis()}",
                platform = platformItem.platform.id,
                platformName = platformItem.displayName,
                remainingDays = 30,
                autoRenew = false,
                packageName = platformItem.packageName,
                cloneInstanceId = null
            )
            val result = shopRepository.createPendingShop(request)
            result.fold(
                onSuccess = {
                    _operationMessageLiveData.value = "店铺卡片已添加"
                    loadShops()
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                }
            )
        }
    }

    fun completePendingShop(pendingShop: Shop, detectedShop: Shop) {
        reportShop(
            detectedShop.copy(cloneInstanceId = detectedShop.cloneInstanceId ?: pendingShop.cloneInstanceId)
        )
    }

    fun updateShop(shop: Shop, newName: String = shop.shopName, autoRenew: Boolean = shop.autoRenew) {
        viewModelScope.launch {
            val request = ShopDto(
                id = shop.id,
                shopName = newName,
                shopId = shop.shopId,
                platform = shop.platform.id,
                platformName = shop.platform.displayName,
                remainingDays = shop.remainingDays,
                autoRenew = autoRenew,
                packageName = shop.packageName,
                cloneInstanceId = shop.cloneInstanceId
            )
            val result = shopRepository.updateShop(shop.id, request)
            result.fold(
                onSuccess = {
                    _operationMessageLiveData.value = "保存成功"
                    loadShops()
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                }
            )
        }
    }

    fun deleteShop(shop: Shop, showMessage: Boolean = true) {
        viewModelScope.launch {
            val result = shopRepository.deleteShop(shop.id)
            result.fold(
                onSuccess = {
                    if (showMessage) {
                        _operationMessageLiveData.value = "删除成功"
                    }
                    loadShops()
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                }
            )
        }
    }

}
