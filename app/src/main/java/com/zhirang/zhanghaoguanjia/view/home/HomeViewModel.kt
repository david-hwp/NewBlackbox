package com.zhirang.zhanghaoguanjia.view.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.Shop
import com.zhirang.zhanghaoguanjia.bean.dto.AnnouncementDto
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformItemDto
import com.zhirang.zhanghaoguanjia.bean.dto.ShopDto
import com.zhirang.zhanghaoguanjia.bean.dto.ShopReportRequest
import com.zhirang.zhanghaoguanjia.data.AnnouncementRepository
import com.zhirang.zhanghaoguanjia.data.PlatformRepository
import com.zhirang.zhanghaoguanjia.data.ShopRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.update.AppUpdateManager
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry

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

    private val _adminLiveData = MutableLiveData<Boolean>()
    val adminLiveData: LiveData<Boolean> = _adminLiveData

    private val _avatarUrlLiveData = MutableLiveData<String?>()
    val avatarUrlLiveData: LiveData<String?> = _avatarUrlLiveData

    private val _latestAnnouncementLiveData = MutableLiveData<AnnouncementDto?>()
    val latestAnnouncementLiveData: LiveData<AnnouncementDto?> = _latestAnnouncementLiveData

    private val _appReleaseAnnouncementLiveData = MutableLiveData<AnnouncementDto?>()
    val appReleaseAnnouncementLiveData: LiveData<AnnouncementDto?> = _appReleaseAnnouncementLiveData

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
    private var pendingShopNameCounter = 0
    private val pendingShopNamePattern = Regex("""^新增店铺-\[(\d+)\]$""")

    init {
        refreshUserInfo()
        loadPlatforms()
        loadShops()
    }

    fun loadLatestAnnouncement() {
        viewModelScope.launch {
            val result = announcementRepository.getPublishedAnnouncements("NORMAL")
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

    fun loadAppReleaseAnnouncementIfNeeded() {
        viewModelScope.launch {
            val versionResult = AppUpdateManager.checkForUpdate(App.getContext())
            versionResult.fold(
                onSuccess = { version ->
                    if (version == null) {
                        _appReleaseAnnouncementLiveData.value = null
                        return@fold
                    }
                    val announcementResult = announcementRepository.getPublishedAnnouncements("APP_RELEASE")
                    announcementResult.fold(
                        onSuccess = { announcements ->
                            _appReleaseAnnouncementLiveData.value = announcements.firstOrNull()
                        },
                        onFailure = {
                            _appReleaseAnnouncementLiveData.value = null
                        }
                    )
                },
                onFailure = {
                    _appReleaseAnnouncementLiveData.value = null
                }
            )
        }
    }

    fun refreshUserInfo() {
        val user = tokenManager.getUser()
        _computeBalanceLiveData.value = user?.computeBalance ?: 0
        _phoneNumberLiveData.value = maskPhoneNumber(user?.phone ?: "")
        _displayUsernameLiveData.value = getDisplayUsername(user?.username, user?.phone)
        _avatarUrlLiveData.value = user?.avatarUrl
        _adminLiveData.value = user?.role.equals("ADMIN", ignoreCase = true)
    }

    fun loadShops() {
        viewModelScope.launch {
            val result = shopRepository.getMyShopsFromApi()
            result.fold(
                onSuccess = { shopDtos ->
                    allShops = shopDtos.map { it.toShop() }
                    _shopsLiveData.value = allShops
                    updatePlatformShopCounts()
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
                    val platforms = platformDtos
                        .map { it.toPlatformItem() }
                        .filter { it.available }
                    PlatformRegistry.update(platforms)
                    _platformsLiveData.value = platforms
                    updatePlatformShopCounts()
                    val selected = _selectedPlatformLiveData.value
                    if (selected == null || platforms.none { it.platform == selected }) {
                        platforms.firstOrNull()?.let {
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
        val packageName = platform?.let { PlatformRegistry.packageName(it) }
        val query = _searchQueryLiveData.value ?: ""
        val availablePackages = _platformsLiveData.value.orEmpty()
            .mapNotNull { it.packageName?.trim()?.takeIf(String::isNotEmpty) }
            .toSet()

        return allShops.filter { shop ->
            val visiblePlatform = shop.packageName?.trim()?.takeIf(String::isNotEmpty) in availablePackages
            val matchPlatform = platform == null || isSamePackage(shop.packageName, packageName)
            val matchQuery = query.isEmpty() ||
                    shop.shopName.contains(query, ignoreCase = true) ||
                    shop.shopId.contains(query, ignoreCase = true)
            visiblePlatform && matchPlatform && matchQuery
        }
    }

    fun getAllShops(): List<Shop> = allShops

    private fun updatePlatformShopCounts() {
        val platforms = _platformsLiveData.value.orEmpty()
        if (platforms.isEmpty()) {
            _platformShopCounts.value = emptyMap()
            return
        }
        _platformShopCounts.value = platforms.associate { item ->
            item.platform to allShops.count { shop ->
                isSamePackage(shop.packageName, item.packageName)
            }
        }
    }

    fun updateComputeBalance(balance: Int) {
        _computeBalanceLiveData.value = balance
    }

    fun getCurrentUserId(): Long {
        return tokenManager.getUser()?.id ?: 0L
    }

    private fun nextPendingShopName(): String {
        val maxExisting = allShops.maxOfOrNull { shop ->
            pendingShopNamePattern.matchEntire(shop.shopName)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull() ?: 0
        } ?: 0
        pendingShopNameCounter = maxOf(pendingShopNameCounter, maxExisting) + 1
        return "新增店铺-[$pendingShopNameCounter]"
    }

    fun reportShop(shop: Shop, showMessage: Boolean = true, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val request = ShopReportRequest(
                shopName = shop.shopName,
                shopId = shop.shopId,
                platform = shop.platform.id,
                platformName = PlatformRegistry.displayName(shop.platform),
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
                    if (it.switchedShop) {
                        _operationMessageLiveData.value = it.message ?: "检测到店铺切换，已创建新店铺并扣划算力"
                    } else if (showMessage) {
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

    fun createPendingShopFromDetectedSwitch(detectedShop: Shop, onSuccess: (Shop) -> Unit) {
        viewModelScope.launch {
            val request = ShopReportRequest(
                shopName = detectedShop.shopName,
                shopId = detectedShop.shopId,
                platform = detectedShop.platform.id,
                platformName = PlatformRegistry.displayName(detectedShop.platform),
                packageName = detectedShop.packageName ?: "",
                cloneInstanceId = null,
                remainingDays = detectedShop.remainingDays,
                autoRenew = detectedShop.autoRenew
            )
            val result = shopRepository.createPendingShopWithDeduction(request)
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
                    _operationMessageLiveData.value = if (it.deducted) {
                        "已扣划 1 点算力，新店铺卡片已添加"
                    } else {
                        "新店铺卡片已存在，正在准备干净分身"
                    }
                    val pendingShop = it.shop.toShop()
                    loadShops()
                    onSuccess(pendingShop)
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                }
            )
        }
    }

    fun createPendingShop(platformItem: PlatformItemDto) {
        viewModelScope.launch {
            val packageName = platformItem.packageName?.takeIf { it.isNotBlank() }
            if (packageName == null) {
                _operationMessageLiveData.value = "该平台暂无关联应用"
                return@launch
            }
            if (allShops.any { it.isNew && isSamePackage(it.packageName, packageName) }) {
                _operationMessageLiveData.value = "您已添加新店铺但未成功登录，请先完成登录后再添加"
                return@launch
            }
            val request = ShopDto(
                id = 0,
                shopName = nextPendingShopName(),
                shopId = "${ShopDto.TEMP_SHOP_ID_PREFIX}${System.currentTimeMillis()}",
                platform = platformItem.platform.id,
                platformName = platformItem.displayName,
                remainingDays = 30,
                autoRenew = false,
                packageName = packageName,
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

    fun completePendingShop(pendingShop: Shop, detectedShop: Shop, showMessage: Boolean = pendingShop.isNew) {
        reportShop(
            detectedShop.copy(cloneInstanceId = detectedShop.cloneInstanceId ?: pendingShop.cloneInstanceId),
            showMessage = showMessage
        )
    }

    fun updateShop(shop: Shop, newName: String = shop.shopName, autoRenew: Boolean = shop.autoRenew) {
        viewModelScope.launch {
            val request = ShopDto(
                id = shop.id,
                shopName = newName,
                shopId = shop.shopId,
                platform = shop.platform.id,
                platformName = PlatformRegistry.displayName(shop.platform),
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

    fun renewShop(shop: Shop, onSuccess: (Shop) -> Unit) {
        viewModelScope.launch {
            val result = shopRepository.renewShop(shop.id)
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
                    _operationMessageLiveData.value = "续期成功"
                    onSuccess(it.shop.toShop())
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

    private fun isSamePackage(left: String?, right: String?): Boolean {
        val normalizedLeft = left?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedRight = right?.trim()?.takeIf { it.isNotEmpty() }
        return normalizedLeft != null && normalizedLeft == normalizedRight
    }
}
