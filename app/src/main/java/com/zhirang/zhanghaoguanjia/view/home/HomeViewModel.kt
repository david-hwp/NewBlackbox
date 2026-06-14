package com.zhirang.zhanghaoguanjia.view.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.bean.Shop
import com.zhirang.zhanghaoguanjia.bean.dto.AnnouncementDto
import com.zhirang.zhanghaoguanjia.bean.dto.AdvancedFeatureDto
import com.zhirang.zhanghaoguanjia.bean.dto.CloneShopCreateRequest
import com.zhirang.zhanghaoguanjia.bean.dto.CloneShopCreateResult
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformItemDto
import com.zhirang.zhanghaoguanjia.bean.dto.ShopAuthTokenRequest
import com.zhirang.zhanghaoguanjia.bean.dto.ShopDto
import com.zhirang.zhanghaoguanjia.bean.dto.ShopReportRequest
import com.zhirang.zhanghaoguanjia.bean.dto.ShopRenewRequest
import com.zhirang.zhanghaoguanjia.bean.dto.UserDto
import com.zhirang.zhanghaoguanjia.data.AnnouncementRepository
import com.zhirang.zhanghaoguanjia.data.AdvancedFeatureRepository
import com.zhirang.zhanghaoguanjia.data.LocalShopIdentityStore
import com.zhirang.zhanghaoguanjia.data.PlatformRepository
import com.zhirang.zhanghaoguanjia.data.ShopRepository
import com.zhirang.zhanghaoguanjia.data.SystemParameterRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.data.UserRepository
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.update.AppUpdateManager
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry
import java.util.UUID

class HomeViewModel : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
        private const val NORMAL_ANNOUNCEMENT_TYPE = "NORMAL"
        private const val APP_RELEASE_ANNOUNCEMENT_TYPE = "APP_RELEASE"
        private const val TICKER_ANNOUNCEMENT_TYPE = "SCROLLING_TICKER"
    }

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

    private val _phoneMinutesBalanceLiveData = MutableLiveData<Int>()
    val phoneMinutesBalanceLiveData: LiveData<Int> = _phoneMinutesBalanceLiveData

    private val _phoneNumberLiveData = MutableLiveData<String>()
    val phoneNumberLiveData: LiveData<String> = _phoneNumberLiveData

    private val _displayUsernameLiveData = MutableLiveData<String>()
    val displayUsernameLiveData: LiveData<String> = _displayUsernameLiveData

    private val _subscriptionStatusLiveData = MutableLiveData<String>()
    val subscriptionStatusLiveData: LiveData<String> = _subscriptionStatusLiveData

    private val _activeSubscriptionLiveData = MutableLiveData<Boolean>()
    val activeSubscriptionLiveData: LiveData<Boolean> = _activeSubscriptionLiveData

    private val _adminLiveData = MutableLiveData<Boolean>()
    val adminLiveData: LiveData<Boolean> = _adminLiveData

    private val _avatarUrlLiveData = MutableLiveData<String?>()
    val avatarUrlLiveData: LiveData<String?> = _avatarUrlLiveData

    private val _latestAnnouncementLiveData = MutableLiveData<AnnouncementDto?>()
    val latestAnnouncementLiveData: LiveData<AnnouncementDto?> = _latestAnnouncementLiveData

    private val _appReleaseAnnouncementLiveData = MutableLiveData<AnnouncementDto?>()
    val appReleaseAnnouncementLiveData: LiveData<AnnouncementDto?> = _appReleaseAnnouncementLiveData

    private val _tickerAnnouncementLiveData = MutableLiveData<AnnouncementDto?>()
    val tickerAnnouncementLiveData: LiveData<AnnouncementDto?> = _tickerAnnouncementLiveData

    private val _platformShopCounts = MutableLiveData<Map<Platform, Int>>()
    val platformShopCounts: LiveData<Map<Platform, Int>> = _platformShopCounts

    private val _loadErrorLiveData = MutableLiveData<String?>()
    val loadErrorLiveData: LiveData<String?> = _loadErrorLiveData

    private val _operationMessageLiveData = MutableLiveData<String?>()
    val operationMessageLiveData: LiveData<String?> = _operationMessageLiveData

    private val shopRepository = ShopRepository(RetrofitClient.apiService)
    private val userRepository = UserRepository(RetrofitClient.apiService)
    private val platformRepository = PlatformRepository(RetrofitClient.apiService)
    private val announcementRepository = AnnouncementRepository(RetrofitClient.apiService)
    private val advancedFeatureRepository = AdvancedFeatureRepository(RetrofitClient.apiService)
    private val systemParameterRepository = SystemParameterRepository(RetrofitClient.apiService)
    private val tokenManager = TokenManager.getInstance()

    private val _appParametersLiveData = MutableLiveData<Map<String, String>>()
    val appParametersLiveData: LiveData<Map<String, String>> = _appParametersLiveData

    private val _advancedFeaturesLiveData = MutableLiveData<Map<String, AdvancedFeatureDto>>()
    val advancedFeaturesLiveData: LiveData<Map<String, AdvancedFeatureDto>> = _advancedFeaturesLiveData

    private var allShops: List<Shop> = emptyList()

    init {
        refreshUserInfo()
        if (isLoggedIn()) {
            loadPlatforms()
            loadShops()
        }
    }

    fun loadLatestAnnouncement() {
        if (!isLoggedIn()) {
            _latestAnnouncementLiveData.value = null
            return
        }
        viewModelScope.launch {
            val result = announcementRepository.getPublishedAnnouncements(NORMAL_ANNOUNCEMENT_TYPE)
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

    fun loadTickerAnnouncement() {
        if (!isLoggedIn()) {
            _tickerAnnouncementLiveData.value = null
            return
        }
        viewModelScope.launch {
            val result = announcementRepository.getPublishedAnnouncements(TICKER_ANNOUNCEMENT_TYPE)
            result.fold(
                onSuccess = { announcements ->
                    _tickerAnnouncementLiveData.value = announcements.firstOrNull()
                },
                onFailure = {
                    _tickerAnnouncementLiveData.value = null
                }
            )
        }
    }

    fun loadAppParameters() {
        viewModelScope.launch {
            systemParameterRepository.getAppParameters().onSuccess { parameters ->
                _appParametersLiveData.value = parameters
            }
        }
    }

    suspend fun getAppParametersForPrompt(): Map<String, String> {
        _appParametersLiveData.value?.let { return it }
        if (!isLoggedIn()) {
            return emptyMap()
        }
        return systemParameterRepository.getAppParameters()
            .onSuccess { parameters -> _appParametersLiveData.value = parameters }
            .getOrDefault(emptyMap())
    }

    fun loadAdvancedFeatures() {
        viewModelScope.launch {
            advancedFeatureRepository.getAppAdvancedFeatures().onSuccess { features ->
                _advancedFeaturesLiveData.value = features
                    .filter { it.code.isNotBlank() }
                    .associateBy { it.code }
            }
        }
    }

    fun getAdvancedFeature(code: String): AdvancedFeatureDto? {
        return _advancedFeaturesLiveData.value?.get(code)
    }

    fun clearOperationMessage() {
        _operationMessageLiveData.value = null
    }

    fun clearLoadError() {
        _loadErrorLiveData.value = null
    }

    fun loadAppReleaseAnnouncementIfNeeded() {
        if (!isLoggedIn()) {
            _appReleaseAnnouncementLiveData.value = null
            return
        }
        viewModelScope.launch {
            val versionResult = AppUpdateManager.checkForUpdate(App.getContext())
            versionResult.fold(
                onSuccess = { version ->
                    if (version == null) {
                        _appReleaseAnnouncementLiveData.value = null
                        return@fold
                    }
                    val announcementResult = announcementRepository.getPublishedAnnouncements(APP_RELEASE_ANNOUNCEMENT_TYPE)
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
        _phoneMinutesBalanceLiveData.value = user?.phoneMinutesBalance ?: 0
        _phoneNumberLiveData.value = maskPhoneNumber(user?.phone ?: "")
        _displayUsernameLiveData.value = getDisplayUsername(user?.username, user?.phone)
        _activeSubscriptionLiveData.value = user?.isSubscriptionActiveNow == true
        _subscriptionStatusLiveData.value = subscriptionStatusText(user)
        _avatarUrlLiveData.value = user?.avatarUrl
        _adminLiveData.value = user?.role.equals("ADMIN", ignoreCase = true)
    }

    fun refreshUserInfoFromServer() {
        if (!isLoggedIn()) {
            refreshUserInfo()
            return
        }
        viewModelScope.launch {
            val result = userRepository.getMe()
            result.fold(
                onSuccess = { user ->
                    tokenManager.saveUser(user)
                    refreshUserInfo()
                },
                onFailure = {
                    refreshUserInfo()
                }
            )
        }
    }

    fun loadShops() {
        if (!isLoggedIn()) {
            allShops = emptyList()
            _shopsLiveData.value = allShops
            _platformShopCounts.value = emptyMap()
            return
        }
        viewModelScope.launch {
            val result = shopRepository.getMyShopsFromApi()
            result.fold(
                onSuccess = { shopDtos ->
                    val currentUserId = getCurrentUserId()
                    allShops = sortShops(shopDtos.map { LocalShopIdentityStore.apply(currentUserId, it.toShop()) })
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
        if (!isLoggedIn()) {
            _platformsLiveData.value = emptyList()
            _platformShopCounts.value = emptyMap()
            return
        }
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

    private fun subscriptionStatusText(user: UserDto?): String {
        if (user?.isSubscriptionActiveNow == true) {
            return user.formattedSubscriptionExpiresAt?.let { "订阅到期：$it" } ?: "订阅生效中"
        }
        return "升级为订阅用户，解锁无上限店铺特权"
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

        return sortShops(allShops.filter { shop ->
            val visiblePlatform = shop.packageName?.trim()?.takeIf(String::isNotEmpty) in availablePackages
            val matchPlatform = platform == null || isSamePackage(shop.packageName, packageName)
            val matchQuery = query.isEmpty() ||
                    shop.shopName.contains(query, ignoreCase = true) ||
                    shop.shopId.contains(query, ignoreCase = true)
            visiblePlatform && matchPlatform && matchQuery
        })
    }

    fun getAllShops(): List<Shop> = allShops

    fun markLocalIdentityVerified(
        shop: Shop,
        packageName: String,
        localVirtualUserId: Int,
        verified: Boolean
    ) {
        LocalShopIdentityStore.mark(getCurrentUserId(), shop, packageName, localVirtualUserId, verified)
        allShops = allShops.map { current ->
            if (current.id == shop.id) {
                current.copy(localIdentityVerified = verified)
            } else {
                current
            }
        }
        _shopsLiveData.value = allShops
    }

    fun getCurrentComputeBalance(): Int {
        return _computeBalanceLiveData.value ?: tokenManager.getUser()?.computeBalance ?: 0
    }

    fun isCurrentUserActiveSubscriber(): Boolean {
        val user = tokenManager.getUser() ?: return false
        return user.hasSubscriptionRecord && user.isSubscriptionActiveNow
    }

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

    fun getCurrentUserPhone(): String {
        return tokenManager.getUser()?.phone.orEmpty()
    }

    fun hasActiveSubscription(): Boolean {
        return tokenManager.getUser()?.isSubscriptionActiveNow == true
    }

    fun hasExpiredSubscriptionRecord(): Boolean {
        val user = tokenManager.getUser() ?: return false
        return user.hasSubscriptionRecord && !user.isSubscriptionActiveNow
    }

    private fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    fun reportShop(
        shop: Shop,
        showMessage: Boolean = true,
        requireVerifiedIdentity: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        if (!isLoggedIn()) {
            _loadErrorLiveData.value = "请先登录后再更新店铺"
            return
        }
        if (requireVerifiedIdentity && !isVerifiedShopIdentity(shop.shopId, shop.shopName)) {
            _loadErrorLiveData.value = "店铺ID和店铺名称需由引擎识别后再更新"
            onComplete?.invoke()
            return
        }
        viewModelScope.launch {
            val request = ShopReportRequest(
                shopName = shop.shopName,
                shopId = shop.shopId,
                platform = shop.platform.id,
                platformName = PlatformRegistry.displayName(shop.platform),
                packageName = shop.packageName ?: "",
                cloneInstanceId = shop.cloneInstanceId,
                localVirtualUserId = shop.localVirtualUserId,
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
                    onComplete?.invoke()
                }
            )
        }
    }

    fun createPendingShopWithClone(
        platformItem: PlatformItemDto,
        localUserId: Int,
        onSuccess: (Shop, CloneShopCreateResult) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!isLoggedIn()) {
            val message = "请先登录后再添加店铺"
            _loadErrorLiveData.value = message
            onFailure(message)
            return
        }
        viewModelScope.launch {
            val packageName = platformItem.packageName?.takeIf { it.isNotBlank() }
            if (packageName == null) {
                val message = "该平台暂无关联应用"
                _operationMessageLiveData.value = message
                onFailure(message)
                return@launch
            }
            val request = CloneShopCreateRequest(
                platform = platformItem.platform.id,
                platformName = platformItem.displayName,
                packageName = packageName,
                localVirtualUserId = localUserId,
                operationKey = "create-${System.currentTimeMillis()}-${UUID.randomUUID()}",
                autoRenew = false
            )
            val result = shopRepository.createCloneShop(request)
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
                    val pendingShop = it.shop.toShop()
                    loadShops()
                    onSuccess(pendingShop, it)
                },
                onFailure = { e ->
                    val message = e.message ?: "创建新店铺失败"
                    _loadErrorLiveData.value = message
                    onFailure(message)
                }
            )
        }
    }

    fun completePendingShop(pendingShop: Shop, detectedShop: Shop, showMessage: Boolean = pendingShop.isNew) {
        reportShop(
            detectedShop.copy(
                cloneInstanceId = detectedShop.cloneInstanceId ?: pendingShop.cloneInstanceId,
                localVirtualUserId = detectedShop.localVirtualUserId ?: pendingShop.localVirtualUserId
            ),
            showMessage = showMessage
        )
    }

    fun updateShop(
        shop: Shop,
        autoRenew: Boolean = shop.autoRenew,
        remark: String? = shop.remark
    ) {
        if (!isLoggedIn()) {
            _loadErrorLiveData.value = "请先登录后再更新店铺"
            return
        }
        viewModelScope.launch {
            val request = ShopDto(
                id = shop.id,
                shopName = shop.shopName,
                shopId = shop.shopId,
                platform = shop.platform.id,
                platformName = PlatformRegistry.displayName(shop.platform),
                cardSortOrder = shop.cardSortOrder,
                remainingDays = shop.remainingDays,
                autoRenew = autoRenew,
                packageName = shop.packageName,
                cloneInstanceId = shop.cloneInstanceId,
                localVirtualUserId = shop.localVirtualUserId,
                wechatReceiverId = shop.wechatReceiverId,
                wechatReceiverName = shop.wechatReceiverName,
                wechatReceiverType = shop.wechatReceiverType,
                remark = remark
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

    fun reorderShops(orderedShops: List<Shop>) {
        if (!isLoggedIn()) {
            _loadErrorLiveData.value = "请先登录后再调整店铺排序"
            return
        }
        val orderedIds = orderedShops.map { it.id }.filter { it > 0 }
        if (orderedIds.size < 2) {
            return
        }
        val orderById = orderedIds.withIndex().associate { it.value to (it.index + 1) * 10 }
        allShops = sortShops(allShops.map { shop ->
            orderById[shop.id]?.let { order -> shop.copy(cardSortOrder = order) } ?: shop
        })
        _shopsLiveData.value = allShops
        viewModelScope.launch {
            val result = shopRepository.reorderShops(orderedIds)
            result.fold(
                onSuccess = { shopDtos ->
                    val currentUserId = getCurrentUserId()
                    allShops = sortShops(shopDtos.map { LocalShopIdentityStore.apply(currentUserId, it.toShop()) })
                    _shopsLiveData.value = allShops
                    updatePlatformShopCounts()
                    _operationMessageLiveData.value = "排序已保存"
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message ?: "保存排序失败"
                    loadShops()
                }
            )
        }
    }

    private fun isVerifiedShopIdentity(shopId: String, shopName: String): Boolean {
        return shopId.isNotBlank()
                && shopId != "-"
                && !shopId.startsWith("NEW-")
                && !shopId.startsWith("phase13-")
                && shopName.isNotBlank()
                && !shopName.startsWith("新增店铺-[")
                && !shopName.startsWith("NEW-")
                && !shopName.startsWith("phase13-")
                && !shopName.startsWith("User[")
                && !shopName.startsWith("未知")
    }

    fun renewShop(shop: Shop, onSuccess: (Shop) -> Unit) {
        renewShopWithToken(shop) { renewedShop, _ ->
            onSuccess(renewedShop)
        }
    }

    fun renewShopWithToken(shop: Shop, onSuccess: (Shop, String?) -> Unit) {
        if (!isLoggedIn()) {
            _loadErrorLiveData.value = "请先登录后再续期"
            return
        }
        viewModelScope.launch {
            val result = shopRepository.renewShop(
                shop.id,
                ShopRenewRequest("renew-${shop.id}-${System.currentTimeMillis()}-${UUID.randomUUID()}")
            )
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
                    onSuccess(it.shop.toShop(), it.authorizationToken)
                    loadShops()
                },
                onFailure = { e ->
                    _loadErrorLiveData.value = e.message
                }
            )
        }
    }

    fun issueShopAuthToken(
        shop: Shop,
        packageName: String,
        localVirtualUserId: Int,
        onSuccess: (Shop, String, String?) -> Unit,
        onFailure: (String) -> Unit,
        showError: Boolean = true
    ) {
        if (!isLoggedIn()) {
            val message = "请先登录后再打开店铺"
            if (showError) {
                _loadErrorLiveData.value = message
            }
            onFailure(message)
            return
        }
        viewModelScope.launch {
            val result = shopRepository.issueShopAuthToken(
                shop.id,
                ShopAuthTokenRequest(
                    localVirtualUserId = localVirtualUserId,
                    packageName = packageName
                )
            )
            result.fold(
                onSuccess = {
                    onSuccess(it.shop.toShop(), it.authorizationToken, it.publicKeyId)
                },
                onFailure = { e ->
                    val message = e.message ?: "获取店铺授权失败"
                    if (showError) {
                        _loadErrorLiveData.value = message
                    }
                    onFailure(message)
                }
            )
        }
    }

    suspend fun downloadLoginState(shopId: Long): Result<ShopRepository.LoginStateDownload?> {
        if (!isLoggedIn()) {
            return Result.failure(Exception("请先登录后再恢复店铺"))
        }
        return shopRepository.downloadLoginState(shopId)
    }

    suspend fun uploadLoginState(shopId: Long, profile: String, manifest: String, artifact: ByteArray): Boolean {
        if (!isLoggedIn() || artifact.isEmpty()) {
            return false
        }
        val result = shopRepository.uploadLoginState(shopId, profile, manifest, artifact)
        result.onFailure { e ->
            Log.w(TAG, "upload login state failed shop=$shopId profile=$profile: ${e.message}")
        }
        return result.isSuccess
    }

    suspend fun uploadLoginStateFile(shopId: Long, profile: String, manifest: String, artifact: java.io.File): Boolean {
        if (!isLoggedIn() || !artifact.exists() || artifact.length() <= 0L) {
            return false
        }
        val result = shopRepository.uploadLoginStateFile(shopId, profile, manifest, artifact)
        result.onFailure { e ->
            Log.w(TAG, "upload login state failed shop=$shopId profile=$profile: ${e.message}")
        }
        return result.isSuccess
    }

    fun deleteShop(shop: Shop, showMessage: Boolean = true) {
        if (!isLoggedIn()) {
            _loadErrorLiveData.value = "请先登录后再删除店铺"
            return
        }
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

    private fun sortShops(shops: List<Shop>): List<Shop> {
        return shops.sortedWith(
            compareBy<Shop> { it.cardSortOrder }
                .thenByDescending { it.id }
        )
    }
}
